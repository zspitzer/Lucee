component extends = "org.lucee.cfml.test.LuceeTestCase" skip="true" {
	function run( testResults, textbox ) {
		describe("Testcase for LDEV-4449 numbers", function() {

			it(title="avoid dive by zero", body=function( currentSpec ) {
				points = [
					[ 0                    , 0  ]
					, [ (10 ^ -14.1305100087), 1  ]
					, [ (10 ^ -13.8630800087), 2  ]
					, [ (10 ^ -13.5956500087), 3  ]
					, [ (10 ^ -13.3282200087), 4  ]
					, [ (10 ^ -13.0607900087), 5  ]
					, [ (10 ^ -12.7933600087), 6  ]
					, [ (10 ^ -12.5259300087), 7  ]
					, [ (10 ^ -12.2585000087), 8  ]
					, [ (10 ^ -11.9910700087), 9  ]
					, [ (10 ^ -11.7236400087), 10 ]
					, [ (10 ^ -11.4562100087), 11 ]
					, [ (10 ^ -11.1887800087), 12 ]
					, [ (10 ^ -10.9213500087), 13 ]
					, [ (10 ^ -10.6539200087), 14 ]
					, [ (10 ^ -10.3864900087), 15 ]
					, [ (10 ^ -10.1190600087), 16 ]
					, [ (10 ^ -9.8516300087) , 17 ]
					, [ (10 ^ -9.5842000087) , 18 ]
					, [ (10 ^ -9.3167700087) , 19 ]
					, [ (10 ^ -9.0493400087) , 20 ]
					, [ (10 ^ -8.7819100087) , 21 ]
					, [ (10 ^ -8.5144800087) , 22 ]
					, [ (10 ^ -8.2470500087) , 23 ]
					, [ (10 ^ -7.9796200087) , 24 ]
					, [ (10 ^ -7.7121900087) , 25 ]
					, [ (10 ^ -7.4447600087) , 26 ]
					, [ (10 ^ -7.1773300087) , 27 ]
					, [ (10 ^ -6.9099000087) , 28 ]
					, [ (10 ^ -6.6424700087) , 29 ]
					, [ (10 ^ -6.3750400087) , 30 ]
					, [ (10 ^ -6.1076100087) , 31 ]
					, [ (10 ^ -5.8401800087) , 32 ]
					, [ (10 ^ -5.5727500087) , 33 ]
					, [ (10 ^ -5.3053200087) , 34 ]
					, [ (10 ^ -5.0378900087) , 35 ]
					, [ (10 ^ -4.7704600087) , 36 ]
					, [ (10 ^ -4.5030300087) , 37 ]
					, [ (10 ^ -4.2356000087) , 38 ]
					, [ (10 ^ -3.9681700087) , 39 ]
					, [ (10 ^ -3.7007400087) , 40 ]
					, [ (10 ^ -3.4333100087) , 41 ]
					, [ (10 ^ -3.1658800087) , 42 ]
					, [ (10 ^ -2.8984500087) , 43 ]
					, [ (10 ^ -2.6310200087) , 44 ]
					, [ (10 ^ -2.3635900087) , 45 ]
					, [ (10 ^ -2.0961600087) , 46 ]
					, [ (10 ^ -1.8287300087) , 47 ]
					, [ (10 ^ -1.5613000087) , 48 ]
					, [ (10 ^ -1.2938700087) , 49 ]
					, [ (10 ^ -1.0264400087) , 50 ]
					, [ (10 ^ -0.7590100087) , 51 ]
					, [ (10 ^ -0.4915800087) , 52 ]
					, [ (10 ^ -0.2241500087) , 53 ]
					, [ (10 ^ 0.0432799913)  , 54 ]
					, [ (10 ^ 0.3107099913)  , 55 ]
					, [ (10 ^ 0.5781399913)  , 56 ]
					, [ (10 ^ 0.8455699913)  , 57 ]
					, [ (10 ^ 1.1129999913)  , 58 ]
					, [ (10 ^ 1.3804299913)  , 59 ]
					, [ (10 ^ 1.6478599913)  , 60 ]
					, [ (10 ^ 1.9152899913)  , 61 ]
					, [ (10 ^ 2.1827199913)  , 62 ]
					, [ (10 ^ 2.4501499913)  , 63 ]
					, [ (10 ^ 2.7175799913)  , 64 ]
					, [ (10 ^ 2.9850099913)  , 65 ]
					, [ (10 ^ 3.2524399913)  , 66 ]
					, [ (10 ^ 3.5198699913)  , 67 ]
					, [ (10 ^ 3.7872999913)  , 68 ]
					, [ (10 ^ 4.0547299913)  , 69 ]
					, [ (10 ^ 4.3221599913)  , 70 ]
					, [ (10 ^ 4.5895899913)  , 71 ]
					, [ (10 ^ 4.8570199913)  , 72 ]
					, [ (10 ^ 5.1244499913)  , 73 ]
					, [ (10 ^ 5.3918799913)  , 74 ]
					, [ (10 ^ 5.6593099913)  , 75 ]
					, [ (10 ^ 5.9267399913)  , 76 ]
					, [ (10 ^ 6.1941699913)  , 77 ]
					, [ (10 ^ 6.4615999913)  , 78 ]
					, [ (10 ^ 6.7290299913)  , 79 ]
					, [ (10 ^ 6.9964599913)  , 80 ]
					, [ (10 ^ 7.2638899913)  , 81 ]
					, [ (10 ^ 7.5313199913)  , 82 ]
					, [ (10 ^ 7.7987499913)  , 83 ]
					, [ (10 ^ 8.0661799913)  , 84 ]
					, [ (10 ^ 8.3336099913)  , 85 ]
					, [ (10 ^ 8.6010399913)  , 86 ]
					, [ (10 ^ 8.8684699913)  , 87 ]
					, [ (10 ^ 9.1358999913)  , 88 ]
					, [ (10 ^ 9.4033299913)  , 89 ]
					, [ (10 ^ 9.6707599913)  , 90 ]
					, [ (10 ^ 9.9381899913)  , 91 ]
					, [ (10 ^ 10.2056199913) , 92 ]
					, [ (10 ^ 10.4730499913) , 93 ]
					, [ (10 ^ 10.7404799913) , 94 ]
					, [ (10 ^ 11.0079099913) , 95 ]
					, [ (10 ^ 11.2753399913) , 96 ]
					, [ (10 ^ 11.5427699913) , 97 ]
					, [ (10 ^ 11.8101999913) , 98 ]
					, [ (10 ^ 12.0776299913) , 99 ]
					, [ (10 ^ 12.3450599913) , 100]
					, [ (10 ^ 20)            , 100]
					, [ (10 ^ 50)            , 120]
					, [ (10 ^ 70)            , 140]
					, [ (10 ^ 90)            , 160]
					, [ (10 ^ 110)           , 180]
					, [ 5 * (10 ^ 130)       , 200]
					, [ (10 ^ 156)           , 220]
					, [ 2 * (10 ^ 197)       , 240]
					, [ 5 * ( 10 ^ 261 )     , 260]
				];

				var inputMap = points.map( function( el ) {
					return el[ 1 ];
				});
				var outputMap = points.map(function( el ) {
					return el[ 2 ];
				});
				systemOutput("", true);
				for( i=1; i < len(points); i++ ) {
					systemOutput("#i# --> (#outputMap[i + 1]# - #outputMap[i]#) / (#inputMap[i + 1]# - #inputMap[i]#)", true);
					delta[i] = ( outputMap[i + 1] - outputMap[i]) / (inputMap[i + 1] - inputMap[i] );
				}
			});
		});
	}
}