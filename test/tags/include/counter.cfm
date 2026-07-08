<cfset request.runOnceCount = ( request.keyExists( "runOnceCount" ) ? request.runOnceCount : 0 ) + 1>
