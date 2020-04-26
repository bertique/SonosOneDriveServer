package me.michaeldick.sonosonedrive.model;

import com.google.gson.JsonObject;

public class Item {
	public enum FileType { file, audio, folder };
	
	FileType type;
	
	String id;
	String name;
	String mimeType;
	int duration;
	String album;
	String artist;	
    String title;
    String parentId;
    String fileUri;
        
    public Item(JsonObject data) {    	    	
		id = data.has("id") ? data.get("id").getAsString() : null;		
    	name = data.has("name") ? data.get("name").getAsString() : null;    	  

    	if(data.has("parentReference")) {
    		JsonObject parentAttributes = data.getAsJsonObject("parentReference");
    		parentId = parentAttributes.has("parentId") ? parentAttributes.get("parentId").getAsString() : null;
    	}
    	
    	if(data.has("file")) {
        	if(data.has("audio")) {
        		type = FileType.audio;
        		JsonObject audioAttributes = data.getAsJsonObject("audio");
        		album = audioAttributes.has("album") ? audioAttributes.get("album").getAsString() : null;
        		artist = audioAttributes.has("artist") ? audioAttributes.get("artist").getAsString() : null;
        		title = audioAttributes.has("title") ? audioAttributes.get("title").getAsString() : null;
        		duration = audioAttributes.has("duration") ? audioAttributes.get("duration").getAsInt() : null;    		
        	} else {
        		type = FileType.file;        		
        	}    		
    		JsonObject fileAttributes = data.getAsJsonObject("file");
    		mimeType = fileAttributes.has("mimeType") ? fileAttributes.get("mimeType").getAsString() : null;
    		fileUri = data.has("@microsoft.graph.downloadUrl") ? data.get("@microsoft.graph.downloadUrl").getAsString() : null;
    		
    	} else if (data.has("folder") ) {
    		type = FileType.folder;
    	}    	    	  
    }

	public FileType getType() {
		return type;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getMimeType() {
		return mimeType;
	}

	public int getDuration() {
		return duration;
	}

	public String getAlbum() {
		return album;
	}

	public String getArtist() {
		return artist;
	}

	public String getTitle() {
		return title;
	}

	public String getParentId() {
		return parentId;
	}
	
	public String getFileUri() {
		return fileUri;
	}
}

