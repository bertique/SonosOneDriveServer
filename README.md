# OneDrive Sonos Service [![Build Status](https://travis-ci.org/bertique/SonosOneDriveServer.svg?branch=master)](https://travis-ci.org/bertique/SonosOneDriveServer)
After building the [NPR One Service for Sonos](https://github.com/bertique/SonosNPROneServer), I recently found myself looking for a way to play my music stored on [OneDrive](). Seeing that OneDrive provides an [easy to user API](https://docs.microsoft.com/en-us/onedrive/developer/rest-api/?view=odsp-graph-online), I built this service.

Most people probably just want to use the [hosted version](https://michaeldick.me/sonos-onedrive/) to add it to your Sonos, but you can also run it yourself.

Issues and pull-requests welcome.

# How do I add OneDrive to my Sonos?
See instructions at [https://michaeldick.me/sonos-onedrive/](https://michaeldick.me/sonos-onedrive/)

# How do I run the service myself?

## Prerequisites
* [Microsoft app registration](https://docs.microsoft.com/en-us/onedrive/developer/rest-api/getting-started/app-registration?view=odsp-graph-online)
* [Heroku account (or similar)](https://heroku.com)

## Run the service locally
* Clone this repo
* Import into Eclipse as Maven project
* Create new Maven build configuration with environment variables:
* GRAPH_CLIENT_ID 
* Generate the ssl key once through Maven: *keytool:generateKeyPair*
* Run Maven target: *tomcat:run-war*

## Run service on Heroku
* Create new Heroku app
* Set environment variables (see above)
* Git push to Heroku. It will use the included procfile
