package com.sentaroh.android.SMBExplorer;

/*
The MIT License (MIT)
Copyright (c) 2011-2013 Sentaroh

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files (the "Software"), to deal 
in the Software without restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to 
the following conditions:

The above copyright notice and this permission notice shall be included in all copies or 
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE 
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

*/

public class FileIoLinkParm {
	FileIoLinkParm () {}
	
    private String fromUrl="", fromDomain="", fromSmbLevel="3", fromUser="", fromPass="", fromBaseUrl="", fromName="";
    private String toUrl="", toDomain="", toSmbLevel="3", toUser="", toPass="", toBaseUrl="", toFileName="";


	public void setFromUrl(String url) {fromUrl=url;}
    public void setFromName(String name) {fromName=name;}
    public void setFromBaseUrl(String url) {fromBaseUrl=url;}
    public void setFromDomain(String domain) {fromDomain=domain;}
    public void setFromSmbLevel(String smb_level) {fromSmbLevel=smb_level;}
    public void setFromUser(String url) {fromUser=url;}
    public void setFromPass(String url) {fromPass=url;}
    public String getFromUrl() {return fromUrl;}
    public String getFromName() {return fromName;}
    public String getFromBaseUrl() {return fromBaseUrl;}
    public String getFromDomain() {return fromDomain;}
    public String getFromSmbLevel() {return fromSmbLevel;}
    public String getFromUser() {return fromUser;}
    public String getFromPass() {return fromPass;}

    public void setToUrl(String url) {toUrl=url;}
    public void setToName(String name) {toFileName=name;}
    public void setToBaseUrl(String url) {toBaseUrl=url;}
    public void setToDomain(String domain) {toDomain=domain;}
    public void setToSmbLevel(String smb_level) {toSmbLevel=smb_level;}
    public void setToUser(String url) {toUser=url;}
    public void setToPass(String url) {toPass=url;}
    public String getToUrl() {return toUrl;}
    public String getToName() {return toFileName;}
    public String getToBaseUrl() {return toBaseUrl;}
    public String getToDomain() {return toDomain;}
    public String getToSmbLevel() {return toSmbLevel;}
    public String getToUser() {return toUser;}
    public String getToPass() {return toPass;}
    

}
