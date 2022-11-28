package me.michaeldick.sonosonedrive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.sonos.services._1.DeviceAuthTokenResult;
import com.sonos.services._1.DeviceLinkCodeResult;
import com.sonos.services._1.LastUpdate;
import com.sonos.services._1_1.CustomFault;

public class SonosServiceTest {

	private static final String PROPERTIES_FILENAME = "debug.properties";
	
	private static SonosService service;
	private static HttpTestServer server;
	
	private static Properties properties;
	
	@BeforeClass
	public static void init() throws Exception {
		properties = new Properties();
		InputStream in = null;
        try {	        	
            in = SonosServiceTest.class.getResourceAsStream("/" + PROPERTIES_FILENAME);
            properties.load(in);

        } catch (IOException e) {
            System.err.println("There was an error reading " + PROPERTIES_FILENAME + ": " + e.getCause()
                    + " : " + e.getMessage());
            System.exit(1);
        } finally {
        	in.close();
        }
        
        server = new HttpTestServer();
        server.start();	        	    
	}
	
	@AfterClass
	public static void destroy() throws Exception {
		server.stop();
	}
	
	@Before
	public void setupTest() {
		service = new SonosService(properties);
	}
	
	@Test
	public void testSonosService() {		
		new SonosService(properties);
	}

	@Ignore
	@Test
	public void testGetScrollIndices() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testAddToContainer() {
		fail("Not yet implemented");
	}
	
	@Ignore
	@Test
	public void testGetExtendedMetadata() throws CustomFault {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testReportPlaySeconds() throws CustomFault {
		fail("Not yet implemented");		
	}

	@Ignore
	@Test
	public void testReportStatus() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testRateItem() throws CustomFault {
		fail("Not yet implemented");						
	}

	@Ignore
	@Test
	public void testReportAccountAction() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testGetExtendedMetadataText() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testRenameContainer() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testSetPlayedSeconds() throws CustomFault {
		fail("Not yet implemented");	
	}
	
	@Test
	public void testGetLastUpdate() throws CustomFault {
		server.setMockResponseData("{\r\n" + 
				"    \"@odata.context\": \"https://graph.microsoft.com/v1.0/$metadata#Collection(driveItem)\",\r\n" + 
				"    \"@odata.nextLink\": \"https://graph.microsoft.com/v1.0/drive/root/delta?expand=thumbnails&token=aTE09NjM3MjM3MTQ3NzUzMDA7SUQ9QTNFQzcyNTA3RTA4MzMyMyExMTc7TFI9NjM3MjM5NjYzMjEwNTA7RVA9MjA7U0k9NTk7RExFUD0wO0xJRD03NTQ4NztGRD1UcnVlO0JVPVRydWU7U0c9MTtTTz0yO1BJPTM\",\r\n" + 
				"    \"value\": [{\r\n" + 
				"            \"@odata.type\": \"#microsoft.graph.driveItem\",\r\n" + 
				"            \"createdDateTime\": \"2010-12-30T12:21:31.97Z\",\r\n" + 
				"            \"cTag\": \"adDpBM0VDNzI1MDdFMDgzMzIzITExNy42MzcyMzcxNDc3NTMwMDAwMDA\",\r\n" + 
				"            \"eTag\": \"aQTNFQzcyNTA3RTA4MzMyMyExMTcuMTg\",\r\n" + 
				"            \"id\": \"A3EC72507E083323!117\",\r\n" + 
				"            \"lastModifiedDateTime\": \"2020-04-28T23:52:55.3Z\",\r\n" + 
				"            \"name\": \"root\",\r\n" + 
				"            \"size\": 147504877866,\r\n" + 
				"            \"webUrl\": \"https://onedrive.live.com/?cid=a3ec72507e083323\",\r\n" + 
				"            \"createdBy\": {\r\n" + 
				"                \"user\": {\r\n" + 
				"                    \"displayName\": \"Michael Dick\",\r\n" + 
				"                    \"id\": \"a3ec72507e083323\"\r\n" + 
				"                }\r\n" + 
				"            },\r\n" + 
				"            \"lastModifiedBy\": {\r\n" + 
				"                \"user\": {\r\n" + 
				"                    \"displayName\": \"Michael Dick\",\r\n" + 
				"                    \"id\": \"a3ec72507e083323\"\r\n" + 
				"                }\r\n" + 
				"            },\r\n" + 
				"            \"parentReference\": {\r\n" + 
				"                \"driveId\": \"a3ec72507e083323\",\r\n" + 
				"                \"driveType\": \"personal\",\r\n" + 
				"                \"id\": \"A3EC72507E083323!0\",\r\n" + 
				"                \"path\": \"/drive/root:\"\r\n" + 
				"            },\r\n" + 
				"            \"fileSystemInfo\": {\r\n" + 
				"                \"createdDateTime\": \"2010-12-30T12:21:31.97Z\",\r\n" + 
				"                \"lastModifiedDateTime\": \"2013-11-04T06:58:49.31Z\"\r\n" + 
				"            },\r\n" + 
				"            \"folder\": {\r\n" + 
				"                \"childCount\": 0,\r\n" + 
				"                \"view\": {\r\n" + 
				"                    \"viewType\": \"thumbnails\",\r\n" + 
				"                    \"sortBy\": \"name\",\r\n" + 
				"                    \"sortOrder\": \"ascending\"\r\n" + 
				"                }\r\n" + 
				"            },\r\n" + 
				"            \"root\": {},\r\n" + 
				"            \"thumbnails\": []\r\n" + 
				"        }, {\r\n" + 
				"            \"@odata.type\": \"#microsoft.graph.driveItem\",\r\n" + 
				"            \"createdDateTime\": \"2020-04-22T00:52:26.333Z\",\r\n" + 
				"            \"cTag\": \"adDpBM0VDNzI1MDdFMDgzMzIzITc0NzQ3LjYzNzIzMzY1MjQxNjc3MDAwMA\",\r\n" + 
				"            \"eTag\": \"aQTNFQzcyNTA3RTA4MzMyMyE3NDc0Ny4w\",\r\n" + 
				"            \"id\": \"A3EC72507E083323!74747\",\r\n" + 
				"            \"lastModifiedDateTime\": \"2020-04-24T22:47:21.677Z\",\r\n" + 
				"            \"name\": \"Musik\",\r\n" + 
				"            \"size\": 36406375270,\r\n" + 
				"            \"webUrl\": \"https://1drv.ms/f/s!ACMzCH5QcuyjhMd7\",\r\n" + 
				"            \"createdBy\": {\r\n" + 
				"                \"application\": {\r\n" + 
				"                    \"id\": \"480728c5\"\r\n" + 
				"                },\r\n" + 
				"                \"device\": {\r\n" + 
				"                    \"id\": \"1840004a0ef879\"\r\n" + 
				"                },\r\n" + 
				"                \"user\": {\r\n" + 
				"                    \"displayName\": \"Michael Dick\",\r\n" + 
				"                    \"id\": \"a3ec72507e083323\"\r\n" + 
				"                },\r\n" + 
				"                \"oneDriveSync\": {\r\n" + 
				"                    \"@odata.type\": \"#microsoft.graph.identity\",\r\n" + 
				"                    \"id\": \"1defab8f-3134-4a12-b388-f3b42b633846\"\r\n" + 
				"                }\r\n" + 
				"            },\r\n" + 
				"            \"lastModifiedBy\": {\r\n" + 
				"                \"application\": {\r\n" + 
				"                    \"id\": \"480728c5\"\r\n" + 
				"                },\r\n" + 
				"                \"device\": {\r\n" + 
				"                    \"id\": \"1840004a0ef879\"\r\n" + 
				"                },\r\n" + 
				"                \"user\": {\r\n" + 
				"                    \"displayName\": \"Michael Dick\",\r\n" + 
				"                    \"id\": \"a3ec72507e083323\"\r\n" + 
				"                },\r\n" + 
				"                \"oneDriveSync\": {\r\n" + 
				"                    \"@odata.type\": \"#microsoft.graph.identity\",\r\n" + 
				"                    \"id\": \"1defab8f-3134-4a12-b388-f3b42b633846\"\r\n" + 
				"                }\r\n" + 
				"            },\r\n" + 
				"            \"parentReference\": {\r\n" + 
				"                \"driveId\": \"a3ec72507e083323\",\r\n" + 
				"                \"driveType\": \"personal\",\r\n" + 
				"                \"id\": \"A3EC72507E083323!117\",\r\n" + 
				"                \"name\": \"root:\",\r\n" + 
				"                \"path\": \"/drive/root:\"\r\n" + 
				"            },\r\n" + 
				"            \"fileSystemInfo\": {\r\n" + 
				"                \"createdDateTime\": \"2020-04-22T00:38:28Z\",\r\n" + 
				"                \"lastModifiedDateTime\": \"2020-04-22T00:40:17Z\"\r\n" + 
				"            },\r\n" + 
				"            \"folder\": {\r\n" + 
				"                \"childCount\": 0,\r\n" + 
				"                \"view\": {\r\n" + 
				"                    \"viewType\": \"thumbnails\",\r\n" + 
				"                    \"sortBy\": \"name\",\r\n" + 
				"                    \"sortOrder\": \"ascending\"\r\n" + 
				"                }\r\n" + 
				"            },\r\n" + 
				"            \"thumbnails\": []\r\n" + 
				"        }]}");
		
		LastUpdate response = service.getLastUpdate();
		
		assertEquals("2020-04-28T23:52:55.3Z", response.getCatalog());
	}
	
	@Test
	public void testGetDeviceLinkCode() throws CustomFault {
		server.setMockResponseData("{\r\n" + 				
				"    \"user_code\": \"[theusercode]\",\r\n" + 
				"    \"device_code\": \"[thedevicecode]\",\r\n" + 
				"    \"verification_uri\": \"[verificationuri]\"\r\n" + 
				"}");
		
		DeviceLinkCodeResult response = service.getDeviceLinkCode("MyTestHousehold");		
		assertEquals("[theusercode]", response.getLinkCode());
		assertEquals("[thedevicecode]", response.getLinkDeviceId());
		assertEquals("[verificationuri]", response.getRegUrl());
	}

	@Ignore
	@Test
	public void testDeleteItem() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetDeviceAuthToken() throws CustomFault {
		server.setMockResponseData("{\r\n" + 
				"    \"token_type\": \"Bearer\",\r\n" + 
				"    \"scope\": \"User.Read profile openid email\",\r\n" + 
				"    \"expires_in\": 3599,\r\n" + 
				"    \"access_token\": \"[accesstoken]\",\r\n" + 
				"    \"refresh_token\": \"[refreshtoken]\",\r\n" + 
				"    \"id_token\": \"eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.eyJhdWQiOiIyZDRkMTFhMi1mODE0LTQ2YTctOD...\"\r\n" + 
				"}");
		
		
		DeviceAuthTokenResult response = service.getDeviceAuthToken("MyTestHousehold", "code", "deviceId", "callback");
		
		assertEquals("[accesstoken]", response.getAuthToken());
		assertEquals("[refreshtoken]", response.getPrivateKey());
		}

	@Ignore
	@Test
	public void testCreateContainer() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testReorderContainer() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testGetStreamingMetadata() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testGetMediaURI() throws CustomFault {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testGetMediaMetadata() throws CustomFault {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testGetMetadata() throws CustomFault {
		fail("Not yet implemented");
	}

	@Ignore
	@Test	
	public void testGetSessionId() throws CustomFault {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testGetContentKey() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testRemoveFromContainer() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testDeleteContainer() {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testReportPlayStatus() throws CustomFault {
		fail("Not yet implemented");
	}

	@Ignore
	@Test
	public void testCreateItem() {
		fail("Not yet implemented");
	}
	
	@Ignore
	@Test
	public void testSearch() throws CustomFault {	
		fail("Not yet implemented");				
	}
}
