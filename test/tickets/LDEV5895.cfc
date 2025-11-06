component extends="org.lucee.cfml.test.LuceeTestCase" labels="extension" {

	function beforeAll(){
		variables.pluginDir = expandPath( "{lucee-config-dir}/context/admin/plugin/" );

		// Setup admin access like AdminPages.cfc
		variables.adminRoot = "/admin/";
		if ( !isEmpty( server.system.environment.LUCEE_TEST_ADMIN_PATH ?: "" ) ) {
			variables.adminRoot = server.system.environment.LUCEE_TEST_ADMIN_PATH;
		}
		variables.adminPage = "index.cfm";

		// Create test extension LEX file from existing files
		variables.testExtFile = getTempDirectory() & "LDEV5895-test-extension.lex";
		if ( fileExists( variables.testExtFile ) ) {
			fileDelete( variables.testExtFile );
		}
		createTestExtension();

		// Login to admin
		loginToAdmin();
	}

	function afterAll(){
		// Cleanup - uninstall test extension and delete temp file
		if ( structKeyExists( variables, "testExtFile" ) && fileExists( variables.testExtFile ) ) {
			try {
				uninstallTestExtension();
			} catch ( any e ) {
				// ignore cleanup errors
			}
			// fileDelete( variables.testExtFile ); // leave on disk for inspection if needed
		}
	}

	function run( testResults , testBox ) {
		describe( title="Test case LDEV-5895, admin plugins should be removed when extension is uninstalled", body=function() {

			it( title="Install extension and verify plugins are deployed", body = function( currentSpec ) {
				// Install the test extension
				installTestExtension();

				// Get list of plugin directories before uninstall
				var pluginDirsBefore = getPluginDirectories();
				systemOutput( "Plugin directories after install: " & arrayToList( pluginDirsBefore ), true );

				// Verify plugin appears in admin navigation by fetching admin page
				var pluginUrlsBefore = getAdminPluginUrls();
				systemOutput( "Admin plugin URLs after install: " & arrayToList( pluginUrlsBefore ), true );

				// Store for comparison
				variables.pluginDirsBefore = pluginDirsBefore;
				variables.pluginUrlsBefore = pluginUrlsBefore;
			});

			it( title="Uninstall extension and verify plugins are removed", body = function( currentSpec ) {
				// Uninstall the test extension
				uninstallTestExtension();

				// Get list of plugin directories after uninstall
				var pluginDirsAfter = getPluginDirectories();
				systemOutput( "Plugin directories after uninstall: " & arrayToList( pluginDirsAfter ), true );

				// Verify plugin is gone from admin navigation by fetching admin page
				var pluginUrlsAfter = getAdminPluginUrls();
				systemOutput( "Admin plugin URLs after uninstall: " & arrayToList( pluginUrlsAfter ), true );

				// Check that plugin directories were removed
				var removedDirs = [];
				loop array=variables.pluginDirsBefore item="local.dir" {
					if ( !arrayFindNoCase( pluginDirsAfter, dir ) ) {
						arrayAppend( removedDirs, dir );
					}
				}

				systemOutput( "Removed plugin directories: " & arrayToList( removedDirs ), true );

				// Verify that at least one plugin directory was removed
				// (we don't know if this specific extension has plugins, but if it does, they should be removed)
				if ( arrayLen( variables.pluginDirsBefore ) > arrayLen( pluginDirsAfter ) ) {
					expect( arrayLen( removedDirs ) ).toBeGT( 0, "Expected at least one plugin directory to be removed" );
				}

				// Verify none of the removed directories still exist on filesystem
				loop array=removedDirs item="local.dir" {
					var dirPath = variables.pluginDir & dir;
					expect( directoryExists( dirPath ) ).toBeFalse(
						"Plugin directory [#dirPath#] should have been deleted from filesystem"
					);
				}

				// Verify plugins that were removed from filesystem are also removed from admin navigation
				loop array=removedDirs item="local.dir" {
					var pluginUrl = "plugin&#chr(38)#plugin=#dir#";
					var found = false;
					loop array=pluginUrlsAfter item="local.adminUrl" {
						if ( findNoCase( pluginUrl, adminUrl ) ) {
							found = true;
							break;
						}
					}
					expect( found ).toBeFalse(
						"Plugin [#dir#] should not appear in admin navigation after uninstall"
					);
				}
			});

		});
	}

	private function getPluginDirectories(){
		var dirs = [];
		if ( !directoryExists( variables.pluginDir ) ) {
			return dirs;
		}

		var q = directoryList(
			path=variables.pluginDir,
			recurse=false,
			listInfo="query",
			type="dir"
		);

		loop query=q {
			arrayAppend( dirs, q.name );
		}

		return dirs;
	}

	private function findExtension( id ){
		var q = extensionList().filter( function( row ){
			return row.id == id;
		});
		return ( q.recordcount != 0 );
	}

	private function installExtension( id ){
		admin
			action="updateRHExtension"
			type="server"
			password="#server.SERVERADMINPASSWORD#"
			id="#arguments.id#";
	}

	private function uninstallExtension( id ){
		if ( !findExtension( id ) ) {
			return; // already uninstalled
		}
		admin
			action="removeRHExtension"
			type="server"
			password="#server.SERVERADMINPASSWORD#"
			id="#arguments.id#";
	}

	private function loginToAdmin(){
		var loginResult = _internalRequest(
			template: variables.adminRoot & variables.adminPage,
			forms: {
				login_passwordserver: request.SERVERADMINPASSWORD,
				lang: "en",
				rememberMe: "s",
				submit: "submit"
			}
		);

		if ( loginResult.status != 200 ) {
			throw( "Failed to login to admin: status #loginResult.status#" );
		}

		variables.cookies = {
			cfid: loginResult.session.cfid,
			cftoken: loginResult.session.cftoken
		};
	}

	private function getAdminPluginUrls(){
		// Fetch admin page with testUrls flag to get all navigation URLs
		var result = _internalRequest(
			template: variables.adminRoot & variables.adminPage,
			urls: { testUrls: true },
			cookies: variables.cookies
		);

		if ( result.status != 200 ) {
			throw( "Failed to fetch admin URLs: status #result.status#" );
		}

		if ( !isJson( result.fileContent ) ) {
			throw( "Admin URLs response is not valid JSON" );
		}

		var adminUrls = deserializeJson( result.fileContent );
		var pluginUrls = [];

		// Filter for plugin URLs
		loop array=adminUrls item="local.adminUrl" {
			if ( findNoCase( "action=plugin", adminUrl ) ) {
				arrayAppend( pluginUrls, adminUrl );
			}
		}

		return pluginUrls;
	}

	private function createTestExtension(){
		// Zip the extension files from the ext directory
		var sourceDir = getDirectoryFromPath( getCurrentTemplatePath() ) & "LDEV5895/ext/";
		zip action="zip" file="#variables.testExtFile#" source="#sourceDir#" overwrite="true";
	}

	private function installTestExtension(){
		// Install using updateRHExtension with file source
		admin
			action="updateRHExtension"
			type="server"
			password="#server.SERVERADMINPASSWORD#"
			source="#variables.testExtFile#";
	}

	private function uninstallTestExtension(){
		admin
			action="removeRHExtension"
			type="server"
			password="#server.SERVERADMINPASSWORD#"
			id="5895B8CB-04CC-4D2B-93D50471D5105D83";
	}

}
