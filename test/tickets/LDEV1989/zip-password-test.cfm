<cfscript>

	param name="url.encryptionAlgorithm";

	workingDir = getDirectoryFromPath( getCurrentTemplatePath() ) & "zip";

	systemOutput( workingDir, true );

	if ( directoryExists( workingDir ) )
		directoryDelete( workingDir, true );

	directoryCreate( workingDir );

	zipPassword = "safePassword";

	zipfile = "#workingDir#/passwordWithEncryptionAlgorithm-#url.encryptionAlgorithm#.zip";

	src = expandPath( '.' );
	systemOutput( src, true );

	zip action="zip" file="#zipFile#"  overwrite="true" password="#zipPassword#" {
		zipparam encryptionAlgorithm="#url.encryptionAlgorithm#" source="#src#" filter="*.cfm";
	}

	systemOutput( "zip created", true );

	zip action="list" file="#zipfile#" name="res";

	systemOutput( res, true );

	unZipDir = workingDir & "/unzipped-#url.encryptionAlgorithm#/";
	if ( !directoryExists( unZipDir ) )
		directoryCreate( unZipDir );

	zip action="unzip" file="#zipFile#" destination="#unzipDir#" password="#zipPassword#";

	systemOutput( directoryList( unzipDir ), true );

	origFileContent = FileRead( getCurrentTemplatePath() );
	outFileContent = FileRead( unzipDir & ListLast( getCurrentTemplatePath(), "\/" ) );

	// test that the contents of this script are the same after zip and unzip with a password and encryptionAlgorithm
	echo ( len(origFileContent) gt 0 && origFileContent eq outFileContent );

</cfscript>
