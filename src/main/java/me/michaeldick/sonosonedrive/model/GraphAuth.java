package me.michaeldick.sonosonedrive.model;

public class GraphAuth {

	String householdId;
	String device_code;
	String refresh_token;
	
	public GraphAuth() {
		this.householdId = null;
		this.device_code = null;
		this.refresh_token = null;
	}
	
	public GraphAuth(String householdId, String device_code, String refresh_token) {
		this.householdId = householdId;
		this.device_code = device_code;
		this.refresh_token = refresh_token;
	}

	public String getHouseholdId() {
		return householdId;
	}

	public String getDeviceCode() {
		return device_code;
	}
	
	public String getRefreshToken() {
		return refresh_token;
	}

	public void setDeviceCode(String device_code) {
		this.device_code = device_code;
	}

	public void setRefreshToken(String refresh_token) {
		this.refresh_token = refresh_token;
	}
}
