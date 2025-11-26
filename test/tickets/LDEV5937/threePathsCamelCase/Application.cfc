component {
	this.name = "LDEV5937_threePathsCamelCase";
	variables.basePath = getDirectoryFromPath( getCurrentTemplatePath() );
	this.functionPaths = [
		variables.basePath & "dir1",
		variables.basePath & "dir2",
		variables.basePath & "dir3"
	];
}
