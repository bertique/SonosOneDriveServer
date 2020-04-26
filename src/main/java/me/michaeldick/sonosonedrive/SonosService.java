package me.michaeldick.sonosonedrive;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.Detail;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.apache.cxf.jaxws.context.WrappedMessageContext;
import org.apache.cxf.message.Message;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mixpanel.mixpanelapi.ClientDelivery;
import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.sonos.services._1.AbstractMedia;
import com.sonos.services._1.AddToContainerResult;
import com.sonos.services._1.AlbumArtUrl;
import com.sonos.services._1.AppLinkResult;
import com.sonos.services._1.ContentKey;
import com.sonos.services._1.CreateContainerResult;
import com.sonos.services._1.Credentials;
import com.sonos.services._1.DeleteContainerResult;
import com.sonos.services._1.DeviceAuthTokenResult;
import com.sonos.services._1.DeviceLinkCodeResult;
import com.sonos.services._1.EncryptionContext;
import com.sonos.services._1.GetExtendedMetadata;
import com.sonos.services._1.GetExtendedMetadataResponse;
import com.sonos.services._1.GetExtendedMetadataText;
import com.sonos.services._1.GetExtendedMetadataTextResponse;
import com.sonos.services._1.GetMediaMetadata;
import com.sonos.services._1.GetMediaMetadataResponse;
import com.sonos.services._1.GetMetadata;
import com.sonos.services._1.GetMetadataResponse;
import com.sonos.services._1.GetSessionId;
import com.sonos.services._1.GetSessionIdResponse;
import com.sonos.services._1.HttpHeaders;
import com.sonos.services._1.ItemType;
import com.sonos.services._1.LastUpdate;
import com.sonos.services._1.LoginToken;
import com.sonos.services._1.MediaCollection;
import com.sonos.services._1.MediaList;
import com.sonos.services._1.MediaMetadata;
import com.sonos.services._1.MediaUriAction;
import com.sonos.services._1.PositionInformation;
import com.sonos.services._1.RateItem;
import com.sonos.services._1.RateItemResponse;
import com.sonos.services._1.RemoveFromContainerResult;
import com.sonos.services._1.RenameContainerResult;
import com.sonos.services._1.ReorderContainerResult;
import com.sonos.services._1.ReportPlaySecondsResult;
import com.sonos.services._1.Search;
import com.sonos.services._1.SearchResponse;
import com.sonos.services._1.TrackMetadata;
import com.sonos.services._1.UserInfo;
import com.sonos.services._1_1.CustomFault;
import com.sonos.services._1_1.SonosSoap;

import me.michaeldick.sonosonedrive.model.GraphAuth;
import me.michaeldick.sonosonedrive.model.Item;

@WebService
public class SonosService implements SonosSoap {

	public static String GRAPH_CLIENT_ID = "";		
	
	public static String MIXPANEL_PROJECT_TOKEN = "";
	
	public static final String PROGRAM = "program";
    public static final String DEFAULT = "default";
    public static final String FOLDER = "folder";
    public static final String FILE = "file";
    public static final String AUDIO = "audio";    
    
    // Error codes
    public static final String SESSION_INVALID = "Client.SessionIdInvalid";
    public static final String LOGIN_INVALID = "Client.LoginInvalid";
    public static final String SERVICE_UNKNOWN_ERROR = "Client.ServiceUnknownError";
    public static final String SERVICE_UNAVAILABLE = "Client.ServiceUnavailable";
    public static final String ITEM_NOT_FOUND = "Client.ItemNotFound"; 
    public static final String AUTH_TOKEN_EXPIRED = "Client.AuthTokenExpired";
    public static final String NOT_LINKED_RETRY = "Client.NOT_LINKED_RETRY";
    public static final String NOT_LINKED_FAILURE = "Client.NOT_LINKED_FAILURE";

    private static final String AUTH_API_URI_DEFAULT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/"; 
    private static final String GRAPH_API_URI_DEFAULT = "https://graph.microsoft.com/v1.0/";
    
    private static String AUTH_API_URI;
    private static String GRAPH_API_URI;
    private static boolean isDebug = false;
        
    // Disable severe log message for SoapFault
    private static java.util.logging.Logger COM_ROOT_LOGGER = java.util.logging.Logger.getLogger("com.sun.xml.internal.messaging.saaj.soap.ver1_1");
    private static Logger logger = Logger.getLogger(SonosService.class.getSimpleName());
    private static MessageBuilder messageBuilder;
    
    @Resource
	private WebServiceContext context;
    
    public WebServiceContext getContext() {
		return this.context;
	}
    
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
    
	@Override
	public String getScrollIndices(String id) throws CustomFault {
		logger.debug("getScrollIndices id:"+id);
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AddToContainerResult addToContainer(String id, String parentId,
			int index, String updateId) throws CustomFault {
		logger.debug("addToContainer");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public GetExtendedMetadataResponse getExtendedMetadata(
			GetExtendedMetadata parameters) throws CustomFault {
		logger.debug("getExtendedMetadata id:"+parameters.getId());


		return null;		
	}

	@Override
	public ReportPlaySecondsResult reportPlaySeconds(String id, int seconds, String contextId, String privateData,
			Integer offsetMillis) throws CustomFault {
		logger.debug("reportPlaySeconds id:"+id+" seconds:"+seconds);
		
		return null;
	}

	@Override
	public void reportStatus(String id, int errorCode, String message)
			throws CustomFault {
		logger.debug("reportStatus");
		// TODO Auto-generated method stub		
	}

	@Override
	public RateItemResponse rateItem(RateItem parameters) throws CustomFault {
		logger.debug("rateItem id:"+parameters.getId()+" rating:"+parameters.getRating());

		
		return null;
	}

	@Override
	public void reportAccountAction(String type) throws CustomFault {
		logger.debug("reportAccountAction");
		// TODO Auto-generated method stub

	}

	@Override
	public GetExtendedMetadataTextResponse getExtendedMetadataText(
			GetExtendedMetadataText parameters) throws CustomFault {
		logger.debug("getExtendedMetadataText id:"+parameters.getId());
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RenameContainerResult renameContainer(String id, String title)
			throws CustomFault {
		logger.debug("renameContainer");
		// TODO Auto-generated method stub
		
		return null;
	}

	@Override
	public void setPlayedSeconds(String id, int seconds, String contextId, String privateData, Integer offsetMillis)
			throws CustomFault {
		logger.debug("setPlayedSeconds id:"+id+" sec:"+seconds);

		
	}

	@Override
	public LastUpdate getLastUpdate() throws CustomFault {
		logger.debug("getLastUpdate");
	
		return null;
	}

	@Override
	public DeviceLinkCodeResult getDeviceLinkCode(String householdId)
			throws CustomFault {	
		logger.debug("getDeviceLinkCode");
		
		JSONObject sentEvent = messageBuilder.event(householdId, "getDeviceLinkCode", null);
	    
        ClientDelivery delivery = new ClientDelivery();
        delivery.addMessage(sentEvent);
        
        MixpanelAPI mixpanel = new MixpanelAPI();
        try {
			mixpanel.deliver(delivery);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			logger.debug("Mixpanel error: getDeviceLinkCode");
		}
        
		Form form = new Form();
		form.param("client_id", GRAPH_CLIENT_ID);						
		form.param("scope", "user.read "+ 
				"files.read "+
				"offline_access");
		
		
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
		
		JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(json);
        String verification_uri = "";
        String user_code = "";
        String device_code = "";
        if (element.isJsonObject()) {
        	JsonObject root = element.getAsJsonObject();
            verification_uri = root.get("verification_uri").getAsString();
            user_code = root.get("user_code").getAsString();
            device_code = root.get("device_code").getAsString();
            logger.info(householdId.hashCode() +": Got verification uri");
        }
		    
        DeviceLinkCodeResult response = new DeviceLinkCodeResult();
		response.setLinkCode(user_code);
		response.setRegUrl(verification_uri);
		response.setLinkDeviceId(device_code);
        response.setShowLinkCode(true);
		return response;
	}

	@Override
	public void deleteItem(String favorite) throws CustomFault {
		logger.debug("deleteItem");
		// TODO Auto-generated method stub

	}

	@Override
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
			JsonParser parser = new JsonParser();
			JsonObject element = parser.parse(e.getResponse().readEntity(String.class)).getAsJsonObject();
			
		    if(element.get("error").getAsString().equals("authorization_pending")) {
				logger.info(householdId.hashCode() +": Not linked retry");				
				throwSoapFault(NOT_LINKED_RETRY, "NOT_LINKED_RETRY", "5");
		    }
			logger.error("Bad request: "+e.getMessage());
			logger.error(e.getResponse().readEntity(String.class));
			throwSoapFault(NOT_LINKED_FAILURE, "NOT_LINKED_FAILURE", "6");
		}
		
		JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(json);
        String access_token = "";
        String refresh_token = ""; 
        if (element.isJsonObject()) {
        	JsonObject root = element.getAsJsonObject();
        	access_token = root.get("access_token").getAsString();      
        	refresh_token = root.get("refresh_token").getAsString();
            logger.info(householdId.hashCode() +": Got token");
        }
		    
        JSONObject sentEvent = messageBuilder.event(householdId, "getDeviceAuthToken", null);
        
        ClientDelivery delivery = new ClientDelivery();
        delivery.addMessage(sentEvent);
        
        MixpanelAPI mixpanel = new MixpanelAPI();
        try {
			mixpanel.deliver(delivery);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			logger.debug("Mixpanel error: getDeviceLinkCode");
		}
        
        DeviceAuthTokenResult response = new DeviceAuthTokenResult();
		response.setAuthToken(access_token);	
		response.setPrivateKey(refresh_token);
		return response;
	}

	@Override
	public CreateContainerResult createContainer(String containerType,
			String title, String parentId, String seedId) throws CustomFault {
		logger.debug("createContainer");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReorderContainerResult reorderContainer(String id, String from,
			int to, String updateId) throws CustomFault {
		logger.debug("reorderContainer");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getMediaURI(String id, MediaUriAction action, Integer secondsSinceExplicit,
			Holder<String> deviceSessionToken, Holder<String> getMediaURIResult,
			Holder<EncryptionContext> deviceSessionKey, Holder<EncryptionContext> contentKey,
			Holder<HttpHeaders> httpHeaders, Holder<Integer> uriTimeout,
			Holder<PositionInformation> positionInformation, Holder<String> privateDataFieldName) throws CustomFault {
		logger.debug("getMediaURI id:"+id);
		
		GraphAuth auth = getGraphAuth();	
				
		String json = graphApiGetRequest("me/drive/items/"+id.replaceAll(SonosService.AUDIO+":","")+"", 1, null, auth);
		
		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(json);
		
		Item m = new Item(element.getAsJsonObject());
            	
		getMediaURIResult.value = m.getFileUri();	
	}

	@Override
	public GetMediaMetadataResponse getMediaMetadata(GetMediaMetadata parameters)
			throws CustomFault {
		logger.debug("getMediaMetadata id:"+parameters.getId());
		
		GraphAuth auth = getGraphAuth();	
		
		String json = graphApiGetRequest("me/drive/items/"+parameters.getId(), 1, null, auth);
		
		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(json);
		
		Item m = new Item(element.getAsJsonObject());
		
		GetMediaMetadataResponse response = new GetMediaMetadataResponse();		
		response.setGetMediaMetadataResult(buildMMD(m));					
		return response;
	}

	@Override
	public GetMetadataResponse getMetadata(GetMetadata parameters)
			throws CustomFault {
		logger.debug("getMetadata id:"+parameters.getId()+" count:"+parameters.getCount()+" index:"+parameters.getIndex());

		GraphAuth auth = getGraphAuth();		
        
        // Mixpanel event
		if(parameters.getId().equals(SonosService.PROGRAM+":"+SonosService.DEFAULT)
			|| parameters.getId().equals(ItemType.SEARCH.value())) {
			
			try {
		        JSONObject props = new JSONObject();
		        props.put("Program", parameters.getId());        
		        
		        JSONObject sentEvent = messageBuilder.event(auth.getHouseholdId(), "getMetadata", props);
		        
		        ClientDelivery delivery = new ClientDelivery();
		        delivery.addMessage(sentEvent);
		        
		        MixpanelAPI mixpanel = new MixpanelAPI();
	        
				mixpanel.deliver(delivery);
			} catch (IOException | JSONException e1) {
				// TODO Auto-generated catch block
				logger.debug("Mixpanel error: getMetadata");
			}
		}        
		
        GetMetadataResponse response = new GetMetadataResponse();
        MediaList ml = new MediaList();
        
		if(parameters.getId().equals("root")) {						
			String path = "/me/drive/root/children";
			String skipToken = null;

			if(parameters.getIndex() > 0) {
				skipToken = getSkipToken(path, parameters.getIndex(), auth);			
			}
			
			String json = graphApiGetRequest(path, parameters.getCount(), skipToken, auth);						
			ml = parseMediaListResponse(auth.getHouseholdId(), json);
			ml.setTotal(ml.getTotal()-1);
		} else if(parameters.getId().startsWith(SonosService.FOLDER)) {
			String path = String.format("/me/drive/items/%s/children", parameters.getId().replaceAll(SonosService.FOLDER+":",""));
			String skipToken = null;
			
			if(parameters.getIndex() > 0) {
				skipToken = getSkipToken(path, parameters.getIndex(), auth);			
			}			
			
			String json = graphApiGetRequest(path, parameters.getCount(), skipToken, auth);						
			ml = parseMediaListResponse(auth.getHouseholdId(), json);
		} else if(parameters.getId().equals(ItemType.SEARCH.value())) {			
									
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
		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(json);			        					
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
					.path(path)
					.queryParam("expand", "thumbnails");
					//.queryParam("filter", "audio ne null or folder ne null");
			if(count > 1) {
				target = target.queryParam("top", count);
			}
			if(skipToken != null) {
				target = target.queryParam("$skipToken", skipToken);
			}
			if(count > 100) {
				target = target.queryParam("select", "id");
			}
			json = target.request(MediaType.APPLICATION_JSON_TYPE)
				  .header("Authorization", "Bearer " + auth.getDeviceCode())
				  .get(String.class);
			client.close();
			
		} catch (NotAuthorizedException e) {
			logger.debug("request NotAuthorized: "+e.getMessage()+", sending AUTH_TOKEN_EXPIRED");
			logger.debug("Device Token"+auth.getDeviceCode());
			logger.debug("Refresh Token"+auth.getRefreshToken());
			logger.error(e.getResponse().readEntity(String.class));
			throwSoapFault(AUTH_TOKEN_EXPIRED);		
		} catch (BadRequestException e) {
			logger.error("Bad request: "+e.getMessage());
			logger.error(e.getResponse().readEntity(String.class));
		}		
		return json;
	}
	
	private static MediaList parseMediaListResponse(String userId, String json) {
		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(json);
	        
		JsonArray mainResultList = element.getAsJsonObject().getAsJsonArray("value");			
		
        if (mainResultList != null) { 
        	MediaList ml = new MediaList();
        	List<AbstractMedia> mcList = ml.getMediaCollectionOrMediaMetadata();    
        	
            for (int i = 0; i < mainResultList.size(); i++) { 
            	Item m = new Item(mainResultList.get(i).getAsJsonObject());
            	if(m.getType() == Item.FileType.folder
            			|| m.getType() == Item.FileType.file) {
            		mcList.add(buildMC(m));            			
            	} else if(m.getType()==Item.FileType.audio) {
            		mcList.add(buildMMD(m));
            	}
			}
			ml.setCount(mcList.size());		
			ml.setTotal(element.getAsJsonObject().get("@odata.count").getAsInt());				
        	logger.debug("Got program list: "+mcList.size());
        	return ml;
        } else {
        	return new MediaList();
        }
	}
	
	private static MediaCollection buildMC(Item m) {	
		MediaCollection mc = new MediaCollection();
				
		if(m.getType().equals(Item.FileType.audio)) {			
			mc.setId(SonosService.AUDIO+":"+m.getId());
			mc.setItemType(ItemType.TRACK);
			if(m.getTitle() != null) {
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
			mc.setCanPlay(false);
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
		mmd.setMimeType(m.getMimeType());		
		mmd.setItemType(ItemType.TRACK);		

		if(m.getTitle() != null) {
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
	
	@Override
	public GetSessionIdResponse getSessionId(GetSessionId parameters)
			throws CustomFault {
		logger.error("getSessionId (deprecated)");
		
		return null;
	}

	@Override
	public ContentKey getContentKey(String id, String uri, String deviceSessionToken) throws CustomFault {
		logger.debug("getContentKey");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RemoveFromContainerResult removeFromContainer(String id,
			String indices, String updateId) throws CustomFault {
		logger.debug("removeFromContainer");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DeleteContainerResult deleteContainer(String id) throws CustomFault {
		logger.debug("deleteContainer");
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void reportPlayStatus(String id, String status, String contextId, Integer offsetMillis) throws CustomFault {		
		logger.debug("reportPlayStatus");
		
	}

	@Override
	public String createItem(String favorite) throws CustomFault {
		logger.debug("createItem favorite:"+favorite);
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SearchResponse search(Search parameters) throws CustomFault {
		logger.debug("search");
		return null;
	}

	@Override
	public AppLinkResult getAppLink(String householdId, String hardware, String osVersion, String sonosAppName,
			String callbackPath) throws CustomFault {
		logger.debug("getAppLink");
		return null;
	}
	
	@Override
	public UserInfo getUserInfo() throws CustomFault {
		logger.debug("getUserInfo");
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public DeviceAuthTokenResult refreshAuthToken() throws CustomFault {
		logger.debug("refreshAuthToken");

		GraphAuth auth = getGraphAuth();		
		
		Form form = new Form();
		form.param("client_id", GRAPH_CLIENT_ID);						
		form.param("refresh_token", auth.getRefreshToken());
		form.param("grant_type", "refresh_token");
		form.param("scope", "user.read "+ 
				"files.read "+
				"offline_access");
		
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
			throwSoapFault(NOT_LINKED_RETRY, "NOT_LINKED_RETRY", "5");
		} catch (BadRequestException e) {
			logger.error("Bad request: "+e.getMessage());
			throwSoapFault(NOT_LINKED_FAILURE, "NOT_LINKED_FAILURE", "6");
		}
		
		JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(json);
        String access_token = "";
        String refresh_token = ""; 
        if (element.isJsonObject()) {
        	JsonObject root = element.getAsJsonObject();
        	access_token = root.get("access_token").getAsString();      
        	refresh_token = root.get("refresh_token").getAsString();
            logger.info(auth.getHouseholdId().hashCode() +": Got token");
        }
		    
        JSONObject sentEvent = messageBuilder.event(auth.getHouseholdId(), "getDeviceAuthToken", null);
        
        ClientDelivery delivery = new ClientDelivery();
        delivery.addMessage(sentEvent);
        
        MixpanelAPI mixpanel = new MixpanelAPI();
        try {
			mixpanel.deliver(delivery);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			logger.debug("Mixpanel error: getDeviceLinkCode");
		}
        
        DeviceAuthTokenResult response = new DeviceAuthTokenResult();
		response.setAuthToken(access_token);	
		response.setPrivateKey(refresh_token);
		return response;
				
				
	}
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Private methods
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////	
	
	private GraphAuth getGraphAuth() {
		Credentials creds = getCredentialsFromHeaders();
		if(creds == null)
			throwSoapFault(SESSION_INVALID);
		
		logger.debug("Got userId from header:"+creds.getLoginToken().getHouseholdId());		
		return new GraphAuth(creds.getLoginToken().getHouseholdId(), creds.getLoginToken().getToken(), creds.getLoginToken().getKey());	
	}
	
	private static void throwSoapFault(String faultMessage) {
		throwSoapFault(faultMessage, "", "");
	}
	
	private static void throwSoapFault(String faultMessage, String ExceptionDetail, String SonosError) throws RuntimeException {
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
