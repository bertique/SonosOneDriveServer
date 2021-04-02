package me.michaeldick.sonosonedrive;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.Holder;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.SoapHeader;
import org.w3c.dom.Node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mixpanel.mixpanelapi.ClientDelivery;
import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.sonos.services.AbstractMedia;
import com.sonos.services.AddToContainerResult;
import com.sonos.services.AlbumArtUrl;
import com.sonos.services.AppLinkResult;
import com.sonos.services.ContentKey;
import com.sonos.services.CreateContainerResult;
import com.sonos.services.Credentials;
import com.sonos.services.DeleteContainerResult;
import com.sonos.services.DeviceAuthTokenResult;
import com.sonos.services.DeviceLinkCodeResult;
import com.sonos.services.EncryptionContext;
import com.sonos.services.GetExtendedMetadata;
import com.sonos.services.GetExtendedMetadataResponse;
import com.sonos.services.GetExtendedMetadataText;
import com.sonos.services.GetExtendedMetadataTextResponse;
import com.sonos.services.GetMediaMetadata;
import com.sonos.services.GetMediaMetadataResponse;
import com.sonos.services.GetMetadata;
import com.sonos.services.GetMetadataResponse;
import com.sonos.services.GetSessionId;
import com.sonos.services.GetSessionIdResponse;
import com.sonos.services.HttpHeaders;
import com.sonos.services.ItemType;
import com.sonos.services.LastUpdate;
import com.sonos.services.LoginToken;
import com.sonos.services.MediaCollection;
import com.sonos.services.MediaList;
import com.sonos.services.MediaMetadata;
import com.sonos.services.MediaUriAction;
import com.sonos.services.PositionInformation;
import com.sonos.services.RateItem;
import com.sonos.services.RateItemResponse;
import com.sonos.services.RemoveFromContainerResult;
import com.sonos.services.RenameContainerResult;
import com.sonos.services.ReorderContainerResult;
import com.sonos.services.ReportPlaySecondsResult;
import com.sonos.services.Search;
import com.sonos.services.SearchResponse;
import com.sonos.services.TrackMetadata;
import com.sonos.services.UserInfo;
import com.sonos.services._1_1.CustomFault;
import com.sonos.services._1_1.SonosSoap;

import me.michaeldick.sonosonedrive.model.GraphAuth;
import me.michaeldick.sonosonedrive.model.Item;

@Endpoint
public class SonosService {

	@Autowired
	MessageSource source;
	
	public static String GRAPH_CLIENT_ID = "";		
	
	public static String MIXPANEL_PROJECT_TOKEN = "";
	
	public static final String PROGRAM = "program";
    public static final String DEFAULT = "default";
    public static final String FOLDER = "folder";
    public static final String FILE = "file";
    public static final String AUDIO = "audio"; 
    public static final String FILES = "files";
    
    // Error codes
    public static final String SESSION_INVALID = "Client.SessionIdInvalid";
    public static final String LOGIN_INVALID = "Client.LoginInvalid";
    public static final String SERVICE_UNKNOWN_ERROR = "Client.ServiceUnknownError";
    public static final String SERVICE_UNAVAILABLE = "Client.ServiceUnavailable";
    public static final String ITEM_NOT_FOUND = "Client.ItemNotFound"; 
    public static final String TOKEN_REFRESH_REQUIRED = "Client.TokenRefreshRequired";
    public static final String AUTH_TOKEN_EXPIRED = "Client.AuthTokenExpired";
    public static final String NOT_LINKED_RETRY = "Client.NOT_LINKED_RETRY";
    public static final String NOT_LINKED_FAILURE = "Client.NOT_LINKED_FAILURE";

    private static final String AUTH_API_URI_DEFAULT = "https://login.microsoftonline.com/common/oauth2/v2.0/"; 
    private static final String GRAPH_API_URI_DEFAULT = "https://graph.microsoft.com/v1.0/";
    private static final String DRIVE_ROOT = "/me/drive/root";
    private static final String DRIVE_APPFOLDER = "/drive/special/approot";
    
    private static String AUTH_API_URI;
    private static String GRAPH_API_URI;
    private static boolean isDebug = false;
        
    // Disable severe log message for SoapFault
    private static java.util.logging.Logger COM_ROOT_LOGGER = java.util.logging.Logger.getLogger("com.sun.xml.internal.messaging.saaj.soap.ver1_1");
    private static Logger logger = Logger.getLogger(SonosService.class.getSimpleName());
    private static MessageBuilder messageBuilder;    
    
    public SonosService(Properties conf) {    	
    	AUTH_API_URI = conf.getProperty("AUTH_API_URI", AUTH_API_URI_DEFAULT);
    	GRAPH_API_URI = conf.getProperty("GRAPH_API_URI", GRAPH_API_URI_DEFAULT);
    	isDebug = Boolean.parseBoolean(conf.getProperty("SETDEBUG", "false"));
    	
    	GRAPH_CLIENT_ID = conf.getProperty("GRAPH_CLIENT_ID", System.getenv("GRAPH_CLIENT_ID"));
    	
    	MIXPANEL_PROJECT_TOKEN = conf.getProperty("MIXPANEL_PROJECT_TOKEN", System.getenv("MIXPANEL_PROJECT_TOKEN"));
    	initializeMetrics();
    	
    	COM_ROOT_LOGGER.setLevel(java.util.logging.Level.OFF);
    }
    
    public SonosService () {
    	AUTH_API_URI = AUTH_API_URI_DEFAULT;
    	GRAPH_API_URI = GRAPH_API_URI_DEFAULT;
    	
    	MIXPANEL_PROJECT_TOKEN = System.getenv("MIXPANEL_PROJECT_TOKEN");
    	GRAPH_CLIENT_ID = System.getenv("GRAPH_CLIENT_ID");    	    
    	initializeMetrics();
    	
    	COM_ROOT_LOGGER.setLevel(java.util.logging.Level.OFF);
    }   
    
    public void initializeMetrics() {    	    	
    	messageBuilder = new MessageBuilder(MIXPANEL_PROJECT_TOKEN);    	
    }
    
    @SuppressWarnings("unused")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getScrollIndices")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "addToContainer")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getExtendedMetadata")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "reportPlaySeconds")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "reportStatus")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "rateItem")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "reportAccountAction")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getExtendedMetadataText")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "renameContainer")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "setPlayedSeconds")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "deleteContainer")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "deleteItem")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "refreshAuthToken")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getUserInfo")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getAppLink")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "reportPlayStatus")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "createItem")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getContentKey")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getSessionId")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "removeFromContainer")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "createContainer")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "reorderContainer")
    @ResponsePayload
    public CustomFault defaultResponse(SoapHeader requestHeader, MessageContext messageContext) {
        logger.warn("Method currently unimplemented: " + messageContext.getRequest().toString());

        CustomFault detail = new CustomFault("Method currently unimplemented: " + messageContext.getRequest().toString());        

        return detail;
    }
  
    @SuppressWarnings("unused")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getLastUpdate")
    @ResponsePayload
	public LastUpdate getLastUpdate() throws CustomFault {
		logger.debug("getLastUpdate");
	
		GraphAuth auth = getGraphAuth();
		
		sentMetricsEvent(auth.getHouseholdId(), "getLastUpdate", null);
		
		String path = DRIVE_ROOT+"/delta";		
		String json = graphApiGetRequest(path, 1, null, auth);						
		
		LastUpdate response = new LastUpdate();        		
		JsonElement element = JsonParser.parseString(json);			        							
		JsonArray mainResultList = element.getAsJsonObject().getAsJsonArray("value");			
		String lastUpdate = null;
		
        if (mainResultList != null && mainResultList.size() > 0) {
        	lastUpdate = mainResultList.get(0).getAsJsonObject().get("lastModifiedDateTime").getAsString();
        }
        logger.debug("Last modifed: "+lastUpdate);
		response.setCatalog(lastUpdate);	
		return response;
	}
	
    @SuppressWarnings("unused")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getDeviceLinkCode")
    @ResponsePayload
	public DeviceLinkCodeResult getDeviceLinkCode(String householdId)
			throws CustomFault {	
		logger.debug("getDeviceLinkCode");
		
		sentMetricsEvent(householdId, "getDeviceLinkCode", null);		
        
		Form form = new Form();
		form.param("client_id", GRAPH_CLIENT_ID);						
				
		if(isAppFolder()) {
			form.param("scope", "user.read "+ 
					"Files.ReadWrite.AppFolder "+
					"offline_access");
		} else {
			form.param("scope", "user.read "+ 
					"files.read "+
					"offline_access");
		}
				
		// https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-device-code		
		String json = "";
		try {
			Client client = ClientBuilder.newClient();
			json = client.target(AUTH_API_URI+"devicecode")
					.request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
					.post(Entity.entity(form,MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);
			client.close();
		} catch (NotAuthorizedException e) {			
			logger.info(householdId.hashCode() +": login NotAuthorized, sending LOGIN_INVALID");
			logger.debug(householdId.hashCode() +": "+e.getMessage());
			logger.error(e.getResponse().readEntity(String.class));
			throwSoapFault(LOGIN_INVALID);
		} catch (BadRequestException e) {
			logger.error("Bad request: "+e.getMessage());
			logger.error(e.getResponse().readEntity(String.class));
			throwSoapFault(SERVICE_UNKNOWN_ERROR);
		}
		
        JsonElement element = JsonParser.parseString(json);
        String verification_uri = "";
        String user_code = "";
        String device_code = "";
        if (element.isJsonObject()) {
        	JsonObject root = element.getAsJsonObject();
            verification_uri = root.has("verification_uri") ? root.get("verification_uri").getAsString() : null;
            user_code = root.has("user_code") ? root.get("user_code").getAsString() : null;
            device_code = root.has("device_code") ? root.get("device_code").getAsString() : null;
            logger.info(householdId.hashCode() +": Got verification uri");
        }
		    
        DeviceLinkCodeResult response = new DeviceLinkCodeResult();
		response.setLinkCode(user_code);
		response.setRegUrl(verification_uri);
		response.setLinkDeviceId(device_code);
        response.setShowLinkCode(true);
		return response;
	}

    @SuppressWarnings("unused")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getDeviceAuthToken")
    @ResponsePayload
	public DeviceAuthTokenResult getDeviceAuthToken(String householdId, String linkCode, String linkDeviceId,
			String callbackPath) throws CustomFault {
		logger.debug("getDeviceAuthToken");
		
		Form form = new Form();
		form.param("client_id", GRAPH_CLIENT_ID);						
		form.param("device_code", linkDeviceId);
		form.param("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
		
		String json = "";
		try {
			Client client = ClientBuilder.newClient();
			json = client.target(AUTH_API_URI + "token")
					.request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
					.post(Entity.entity(form,MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);
			client.close();
		} catch (NotAuthorizedException e) {
			logger.info(householdId.hashCode() +": Not linked retry");
			logger.debug(householdId.hashCode() +": "+e.getMessage());
			logger.debug(householdId.hashCode() +": Detailed response: "+e.getResponse().readEntity(String.class));
			throwSoapFault(NOT_LINKED_RETRY, "NOT_LINKED_RETRY", "5");
		} catch (BadRequestException e) {
			JsonObject element = JsonParser.parseString(e.getResponse().readEntity(String.class)).getAsJsonObject();			
			
		    if(element.get("error").getAsString().equals("authorization_pending")) {
				logger.info(householdId.hashCode() +": Not linked retry");				
				throwSoapFault(NOT_LINKED_RETRY, "NOT_LINKED_RETRY", "5");
		    }
			logger.error("Bad request: "+e.getMessage());
			logger.error(e.getResponse().readEntity(String.class));
			throwSoapFault(NOT_LINKED_FAILURE, "NOT_LINKED_FAILURE", "6");
		}
		
		JsonElement element = JsonParser.parseString(json);
        String access_token = "";
        String refresh_token = ""; 
        if (element.isJsonObject()) {
        	JsonObject root = element.getAsJsonObject();
        	access_token = root.has("access_token") ? root.get("access_token").getAsString() : null;      
        	refresh_token = root.has("refresh_token") ? root.get("refresh_token").getAsString() : null;
            logger.info(householdId.hashCode() +": Got token");
        }

        if(access_token.length() > 2048) {		
        	access_token = compressToken(access_token);
        }
		               
		sentMetricsEvent(householdId, "getDeviceAuthToken", null);		        
        
        DeviceAuthTokenResult response = new DeviceAuthTokenResult();
		response.setAuthToken(access_token);	
		response.setPrivateKey(refresh_token);
		return response;
	}

    @SuppressWarnings("unused")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getMediaURI")
    @ResponsePayload
	public void getMediaURI(String id, MediaUriAction action, Integer secondsSinceExplicit,
			Holder<String> deviceSessionToken, Holder<String> getMediaURIResult,
			Holder<EncryptionContext> deviceSessionKey, Holder<EncryptionContext> contentKey,
			Holder<HttpHeaders> httpHeaders, Holder<Integer> uriTimeout,
			Holder<PositionInformation> positionInformation, Holder<String> privateDataFieldName) throws CustomFault {
		logger.debug("getMediaURI id:"+id);
		
		GraphAuth auth = getGraphAuth();	
				
		String json = graphApiGetRequest("me/drive/items/"+id.replaceAll(SonosService.AUDIO+":","")+"", 1, null, auth);
		
		JsonElement element = JsonParser.parseString(json);
		
		Item m = new Item(element.getAsJsonObject());
            	
		getMediaURIResult.value = m.getFileUri();	
	}

    @SuppressWarnings("unused")
    @PayloadRoot(namespace = MusicServiceConfig.SONOS_SOAP_NAMESPACE, localPart = "getMediaMetadata")
    @ResponsePayload
	public GetMediaMetadataResponse getMediaMetadata(GetMediaMetadata parameters)
			throws CustomFault {
		logger.debug("getMediaMetadata id:"+parameters.getId());
		
		GraphAuth auth = getGraphAuth();	
		
		String json = graphApiGetRequest("me/drive/items/"+parameters.getId(), 1, null, auth);
					
		JsonElement element = JsonParser.parseString(json);
		
		Item m = new Item(element.getAsJsonObject());
		
		GetMediaMetadataResponse response = new GetMediaMetadataResponse();		
		response.setGetMediaMetadataResult(buildMMD(m));					
		return response;
	}

	@SuppressWarnings("unused")
    @ResponsePayload
	public GetMetadataResponse getMetadata(GetMetadata parameters)
			throws CustomFault {
		logger.debug("getMetadata id:"+parameters.getId()+" count:"+parameters.getCount()+" index:"+parameters.getIndex());

		GraphAuth auth = getGraphAuth();		
        
        // Mixpanel event
//		if(parameters.getId().equals(SonosService.PROGRAM+":"+SonosService.DEFAULT)
//			|| parameters.getId().equals(ItemType.SEARCH.value())) {
//						
//		        JSONObject props = new JSONObject();
//		        try {
//					props.put("Program", parameters.getId());
//				} catch (JSONException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}        
//		        
//		        sentMetricsEvent(auth.getHouseholdId(), "getMetadata", props);			        		      
//		}        
		
        GetMetadataResponse response = new GetMetadataResponse();
        MediaList ml = new MediaList();
        
		if(parameters.getId().equals("root")) {						
			String path = DRIVE_ROOT+"/children";
			
			if(isAppFolder()) {
				path = DRIVE_APPFOLDER+"/children";
			}
			
			String skipToken = null;

			if(parameters.getIndex() > 0) {
				skipToken = getSkipToken(path, parameters.getIndex(), auth);			
			}
			
			String json = graphApiGetRequest(path, parameters.getCount(), skipToken, auth);						
			ml = parseMediaListResponse(auth.getHouseholdId(), json);				
		} else if(parameters.getId().startsWith(SonosService.FOLDER)) {
			String path = String.format("/me/drive/items/%s/children", parameters.getId().replaceAll(SonosService.FOLDER+":",""));
			String skipToken = null;
			
			if(parameters.getIndex() > 0) {
				skipToken = getSkipToken(path, parameters.getIndex(), auth);			
			}			
			
			String json = graphApiGetRequest(path, parameters.getCount(), skipToken, auth);						
			ml = parseMediaListResponse(auth.getHouseholdId(), json);
		} else if(parameters.getId().equals(ItemType.SEARCH.value())) {						
			List<AbstractMedia> mcList = ml.getMediaCollectionOrMediaMetadata();
			
			MediaCollection mc1 = new MediaCollection();			
			mc1.setTitle("Files");
			mc1.setId(SonosService.FILES);
			mc1.setItemType(ItemType.SEARCH);
			mc1.setCanPlay(false);
			mcList.add(mc1);			 					
			
			ml.setCount(mcList.size());
			ml.setTotal(mcList.size());
		} else {
			return null;
		}
		
		ml.setIndex(parameters.getIndex());
		response.setGetMetadataResult(ml);
				
		logger.info(auth.getHouseholdId().hashCode() + ": Got Metadata for "+parameters.getId()+", "+response.getGetMetadataResult().getCount()+" Index:"+ml.getIndex()+" Count:"+ml.getCount()+ " Total: "+ml.getTotal());
		return response;
	}	
	
	private String getSkipToken(String path, int index, GraphAuth auth) {
		String skipToken = null;
		String json = graphApiGetRequest(path, index, null, auth);
		JsonElement element = JsonParser.parseString(json);					        				
		Pattern pattern = Pattern.compile("\\$skiptoken=(.+)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(element.getAsJsonObject().get("@odata.nextLink").getAsString());
		if (matcher.find())
		{
		    skipToken = matcher.group(1);
		}
		int numResults = element.getAsJsonObject().getAsJsonArray("value").size();
		logger.debug(String.format("Iteration Complete results %d, goalIndex %d", numResults, index));
		logger.debug("SkipToken "+skipToken);
		return skipToken;
	}
	
	private String graphApiGetRequest(String path, int count, String skipToken, GraphAuth auth) {
		String json = "";
		try {	
			Client client = ClientBuilder.newClient();
			WebTarget target = client
					.target(GRAPH_API_URI)
					.path(path);
					//.queryParam("filter", "audio ne null or folder ne null");
			if(!path.contains("delta")) {
				target = target.queryParam("expand", "thumbnails");
			}
			if(count > 1) {
				target = target.queryParam("top", count);
			}
			if(skipToken != null) {
				target = target.queryParam("$skipToken", skipToken);
			}
			if(count > 100 && count != Integer.MAX_VALUE) {
				target = target.queryParam("select", "id");
			}		
			json = target.request(MediaType.APPLICATION_JSON_TYPE)
				  .header("Authorization", "Bearer " + auth.getDeviceCode())
				  .get(String.class);
			client.close();
			
		} catch (NotAuthorizedException e) {
			logger.debug("request NotAuthorized: "+e.getMessage()+", trying to refresh token");		
			logger.error(e.getResponse().readEntity(String.class));
			GraphAuth newAuth = refreshToken();
			throwSoapFault(TOKEN_REFRESH_REQUIRED, newAuth);		
		} catch (BadRequestException e) {
			logger.error("Bad request: "+e.getMessage());
			logger.error(e.getResponse().readEntity(String.class));
		}		
		return json;
	}
	
	private static MediaList parseMediaListResponse(String userId, String json) {
		JsonElement element = JsonParser.parseString(json);		
	        
		JsonArray mainResultList = element.getAsJsonObject().getAsJsonArray("value");			
		
        if (mainResultList != null) { 
        	MediaList ml = new MediaList();
        	List<AbstractMedia> mcList = ml.getMediaCollectionOrMediaMetadata();    
        	
            for (int i = 0; i < mainResultList.size(); i++) { 
            	Item m = new Item(mainResultList.get(i).getAsJsonObject());
            	if(m.getType()==Item.FileType.audio 
            			|| (m.getType()==Item.FileType.file && m.getName().endsWith(".flac"))
            			|| (m.getType().equals(Item.FileType.file) && m.getMimeType().contains("audio"))) {
            		mcList.add(buildMMD(m));
            	} else if(m.getType() == Item.FileType.folder
            			|| m.getType() == Item.FileType.file) {
            		mcList.add(buildMC(m));            			
            	}
			}
			ml.setCount(mcList.size());
			if(element.getAsJsonObject().has("@odata.count")) {
				ml.setTotal(element.getAsJsonObject().get("@odata.count").getAsInt());
				if(ml.getTotal()<100 && ml.getTotal()>mcList.size()) {
					ml.setTotal(ml.getTotal()-1);
				}
			} else {
				ml.setTotal(mcList.size());
			}							
        	logger.debug("Got program list: "+mcList.size());
        	return ml;
        } else {
        	return new MediaList();
        }
	}
	
	private static MediaCollection buildMC(Item m) {	
		MediaCollection mc = new MediaCollection();
				
		if(m.getType().equals(Item.FileType.audio)
				|| (m.getType().equals(Item.FileType.file) && m.getName().endsWith(".flac"))
				|| (m.getType().equals(Item.FileType.file) && m.getMimeType().contains("audio"))) {			
			mc.setId(SonosService.AUDIO+":"+m.getId());
			mc.setItemType(ItemType.TRACK);
			if(m.getTitle() != null && !m.getTitle().isEmpty()) {
				mc.setTitle(m.getTitle());
			} else {
				mc.setTitle(m.getName());
			}
			if(m.getArtist() != null) {
				mc.setArtist(m.getArtist());
			}
			if(m.getThumbnail() != null) {
				AlbumArtUrl art = new AlbumArtUrl();
				art.setValue(m.getThumbnail());
				mc.setAlbumArtURI(art);
			}
			mc.setCanPlay(true);
			mc.setCanEnumerate(false);
			
		} else if(m.getType().equals(Item.FileType.file)) {
			mc.setId(SonosService.FILE+":"+m.getId());
			mc.setItemType(ItemType.OTHER);
			mc.setTitle(m.getName());
			
			mc.setCanPlay(false);
			mc.setCanEnumerate(false);
		} else if(m.getType().equals(Item.FileType.folder)) {
			mc.setId(SonosService.FOLDER+":"+m.getId());
			mc.setItemType(ItemType.COLLECTION);
			mc.setTitle(m.getName());
			if(m.getChildCount() < 40) {
				mc.setCanPlay(true);
			} else {
				mc.setCanPlay(false);
			}			
			mc.setCanEnumerate(true);	
		}
			
		return mc;
	}

	private static MediaMetadata buildMMD(Item m) {
		MediaMetadata mmd = new MediaMetadata();
		TrackMetadata tmd = new TrackMetadata();
		if(m==null)
			return null;
		
		mmd.setId(m.getId());
		if(m.getType().equals(Item.FileType.file) && m.getName().endsWith(".flac")) {
			mmd.setMimeType("audio/flac");
		} else {
			mmd.setMimeType(m.getMimeType());	
		}
		mmd.setItemType(ItemType.TRACK);
		mmd.setDisplayType("audio");

		if(m.getTitle() != null && !m.getTitle().isEmpty()) {
			mmd.setTitle(m.getTitle());
		} else {
			mmd.setTitle(m.getName());
		}
		
		if(m.getArtist() != null) {
			tmd.setArtist(m.getArtist());
		}								
		if(m.getAlbum() != null) {
			tmd.setAlbum(m.getAlbum());
		}
		
		if (m.getDuration() > 0) {
			tmd.setDuration(m.getDuration());
		}
		if(m.getThumbnail() != null) {
			AlbumArtUrl art = new AlbumArtUrl();
			art.setValue(m.getThumbnail());
			tmd.setAlbumArtURI(art);
		}
		tmd.setTrackNumber(m.getTrack());
		
		mmd.setTrackMetadata(tmd);		
		return mmd;
	}	

	public SearchResponse search(Search parameters) throws CustomFault {
		logger.debug("search");
		
		GraphAuth auth = getGraphAuth();
		
		String path = String.format("/me/drive/root/search(q='%s')", parameters.getTerm());
		String skipToken = null;

		if(parameters.getIndex() > 0) {
			skipToken = getSkipToken(path, parameters.getIndex(), auth);			
		}
		
		String json = graphApiGetRequest(path, parameters.getCount(), skipToken, auth);						
		SearchResponse response = new SearchResponse();
        MediaList ml = new MediaList();
		ml = parseMediaListResponse(auth.getHouseholdId(), json);
		// Remove 1 since personal vault is not included in response
		ml.setTotal(ml.getTotal());
		ml.setIndex(parameters.getIndex());
		response.setSearchResult(ml);	
		return response;
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Private methods
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////	
	
	private String compressToken(String token) {
		logger.info("Access token too long, compressing...");
		String compressed_token = null;
		String[] tokenParts = token.split("\\.");			
		if(tokenParts.length==3) {						
			byte[] decodedFirstTokenPart = Base64.getDecoder().decode(tokenParts[0]);			
			byte[] decodedSecondTokenPart = Base64.getDecoder().decode(tokenParts[1]);		
			compressed_token = String.format("%s###%s###%s", new String(decodedFirstTokenPart), new String(decodedSecondTokenPart), tokenParts[2]);
			if (compressed_token.length() > 2048) {
				logger.error("Compressed token too long");
			}
		} else {
			throwSoapFault(NOT_LINKED_FAILURE);		
		}
		return compressed_token;
	}
	
	private void sentMetricsEvent(String userId, String eventName, JSONObject properties) {
		JSONObject sentEvent = messageBuilder.event(userId, eventName, properties);
	    
        ClientDelivery delivery = new ClientDelivery();
        delivery.addMessage(sentEvent);
        
        MixpanelAPI mixpanel = new MixpanelAPI();
        try {
			mixpanel.deliver(delivery);
		} catch (IOException e1) {
			logger.debug("Mixpanel error: "+eventName);
		}
	}
	
	private GraphAuth getGraphAuth() {
		Credentials creds = getCredentialsFromHeaders();
		if(creds == null)
			throwSoapFault(SESSION_INVALID);
		
		logger.debug("Got userId from header:"+creds.getLoginToken().getHouseholdId());		
		String access_token = null;
		if(creds.getLoginToken().getToken().startsWith("{")
				&&creds.getLoginToken().getToken().contains("###")) {
		
			String[] tokenParts = creds.getLoginToken().getToken().split("###");
			if(tokenParts.length==3) {						
				byte[] encodedFirstTokenPart = Base64.getEncoder().encode(tokenParts[0].getBytes());			
				byte[] encodedSecondTokenPart = Base64.getEncoder().encode(tokenParts[1].getBytes());		
				access_token = String.format("%s.%s.%s", new String(encodedFirstTokenPart).replace("=", ""), new String(encodedSecondTokenPart).replace("=", ""), tokenParts[2]);
			} else {
				throwSoapFault(NOT_LINKED_FAILURE);		
			}
		} else {
			access_token = creds.getLoginToken().getToken();
		}
		
		return new GraphAuth(creds.getLoginToken().getHouseholdId(), access_token, creds.getLoginToken().getKey());	
	}
	
	private GraphAuth refreshToken() {
		GraphAuth auth = getGraphAuth();		
		
		Form form = new Form();
		form.param("client_id", GRAPH_CLIENT_ID);						
		form.param("refresh_token", auth.getRefreshToken());
		form.param("grant_type", "refresh_token");		
		
		if(isAppFolder()) {
			form.param("scope", "user.read "+ 
					"Files.ReadWrite.AppFolder "+
					"offline_access");
		} else {
			form.param("scope", "user.read "+ 
					"files.read "+
					"offline_access");
		}
		
		String json = "";
		try {
			Client client = ClientBuilder.newClient();
			json = client.target(AUTH_API_URI + "token")
					.request(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
					.post(Entity.entity(form,MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);
			client.close();
		} catch (NotAuthorizedException e) {
			logger.info(auth.getHouseholdId().hashCode() +": Not linked retry");
			logger.debug(auth.getHouseholdId().hashCode() +": "+e.getMessage());
			logger.debug(auth.getHouseholdId().hashCode() +": Detailed response: "+e.getResponse().readEntity(String.class));
			throwSoapFault(AUTH_TOKEN_EXPIRED);
		} catch (BadRequestException e) {
			logger.error("Bad request: "+e.getMessage());
			logger.debug(auth.getHouseholdId().hashCode() +": Detailed response: "+e.getResponse().readEntity(String.class));
			JsonParser parser = new JsonParser();
	        JsonElement element = parser.parse(e.getResponse().readEntity(String.class));
	        if(element.isJsonObject()) {
	        	JsonObject o = element.getAsJsonObject();
	        	if(o.has("error") && o.get("error").getAsString().equals("invalid_grant")) {
	        		throwSoapFault(AUTH_TOKEN_EXPIRED);
	        	}
	        }
			throwSoapFault(SERVICE_UNKNOWN_ERROR);
		}
		
		JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(json);
        GraphAuth newAuth = new GraphAuth(); 
        if (element.isJsonObject()) {
        	JsonObject root = element.getAsJsonObject();
        	
        	String access_token = root.get("access_token").getAsString();
        	String refresh_token = root.get("refresh_token").getAsString();
        	
        	if(access_token.length() > 2048) {	
        		access_token = compressToken(access_token);
            }
        	        	        
        	newAuth.setDeviceCode(access_token);      
        	newAuth.setRefreshToken(refresh_token);
            logger.info(auth.getHouseholdId().hashCode() +": Got refreshed token");
        }
		           
        return newAuth;
	}
	
	private boolean isAppFolder() {
		if(this.context != null) {
			return this.context.getMessageContext().get("org.apache.cxf.request.url").toString().contains("soap_appfolder");		
		} else {
			return false;
		}		
	}
	
	private static void throwSoapFault(String faultMessage) {
		throwSoapFault(faultMessage, "", "", null);
	}
	
	private static void throwSoapFault(String faultMessage, GraphAuth newAuth) {
		throwSoapFault(faultMessage, "", "", newAuth);
	}
	
	private static void throwSoapFault(String faultMessage, String ExceptionDetail, String SonosError) {
		throwSoapFault(faultMessage, ExceptionDetail, SonosError, null);
	}
	
	private static void throwSoapFault(String faultMessage, String ExceptionDetail, String SonosError, GraphAuth newAuth) throws RuntimeException {
		SOAPFault soapFault;
		try {
            soapFault = SOAPFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL).createFault();
            soapFault.setFaultString(faultMessage);
            soapFault.setFaultCode(new QName(faultMessage));
            
            if(!ExceptionDetail.isEmpty() && !SonosError.isEmpty()) {            
            	Detail detail = soapFault.addDetail();
            	SOAPElement el1 = detail.addChildElement("ExceptionDetail");
    	        el1.setValue(ExceptionDetail);
	            SOAPElement el = detail.addChildElement("SonosError");
	            el.setValue(SonosError);	            
            }
            if(newAuth != null) {
            	Detail detail = soapFault.addDetail();
            	SOAPElement container = detail.addChildElement("refreshAuthTokenResult");
            	SOAPElement el1 = container.addChildElement("authToken");
            	el1.setValue(newAuth.getDeviceCode());
            	SOAPElement el2 = container.addChildElement("privateKey");
            	el2.setValue(newAuth.getRefreshToken());
            }
            
        } catch (Exception e2) {
            throw new RuntimeException("Problem processing SOAP Fault on service-side." + e2.getMessage());
        }
            throw new SOAPFaultException(soapFault);

    }		
	
	private Credentials getCredentialsFromHeaders() {
		if(isDebug) {
			Credentials c = new Credentials();
			LoginToken t = new LoginToken();
			t.setHouseholdId("[thehouseholdid]");
			t.setToken("[thetoken]");
			c.setLoginToken(t);
			return c;
		}
		if(context == null)
			return null;
		MessageContext messageContext = context.getMessageContext();
		if (messageContext == null
				|| !(messageContext instanceof WrappedMessageContext)) {
			logger.error("Message context is null or not an instance of WrappedMessageContext.");
			return null;
		}

		Message message = ((WrappedMessageContext) messageContext)
				.getWrappedMessage();
		List<Header> headers = CastUtils.cast((List<?>) message
				.get(Header.HEADER_LIST));
		if (headers != null) {
			for (Header h : headers) {
				Object o = h.getObject();
				// Unwrap the node using JAXB
				if (o instanceof Node) {
					JAXBContext jaxbContext;
					try {
						jaxbContext = new JAXBDataBinding(Credentials.class)
								.getContext();
						Unmarshaller unmarshaller = jaxbContext
								.createUnmarshaller();
						o = unmarshaller.unmarshal((Node) o);
					} catch (JAXBException e) {
						// failed to get the credentials object from the headers
						logger.error(
								"JaxB error trying to unwrapp credentials", e);
					}
				}
				if (o instanceof Credentials) {
					return (Credentials) o;										
				} else {
					logger.error("no Credentials object");
				}
			}
		} else {
			logger.error("no headers found");
		}
		return null;
	}
}
