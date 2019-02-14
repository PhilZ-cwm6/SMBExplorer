package com.sentaroh.android.SMBExplorer;

import com.sentaroh.jcifs.JcifsAuth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

class SmbServerConfig implements Serializable, Cloneable, Comparable<SmbServerConfig>{
	private String profileType="R";
	private String profileName="No name";
    private String profileDomain="";
	private String profileUser="";
	private String profilePass="";
	private String profileAddr="";
	private String profilePort="";
	private String profileShare="";
    private String profileSmbLevel=String.valueOf(JcifsAuth.JCIFS_FILE_SMB211);
	private boolean profileIsChecked=false;

    @Override
    public SmbServerConfig clone() {
        SmbServerConfig npfli = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);

            oos.flush();
            oos.close();

            baos.flush();
            byte[] ba_buff = baos.toByteArray();
            baos.close();

            ByteArrayInputStream bais = new ByteArrayInputStream(ba_buff);
            ObjectInputStream ois = new ObjectInputStream(bais);

            npfli = (SmbServerConfig) ois.readObject();
            ois.close();
            bais.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return npfli;
    }

    public SmbServerConfig(){}

    public SmbServerConfig(String pfn,
                           String domain, String pf_user, String pf_pass, String pf_addr, String pf_port, String pf_share){
        profileType = "R";
        profileName = pfn;
        profileDomain = domain;
        profileUser = pf_user;
        profilePass = pf_pass;
        profileAddr = pf_addr;
        profilePort = pf_port;
        profileShare = pf_share;
        profileIsChecked = false;

    }

    public String getType(){return profileType;}
	public String getName(){return profileName;}
    public void setName(String name){profileName=name;}

    public String getDomain(){return profileDomain;}
    public void setDomain(String domain){profileDomain=domain;}

    public String getUser(){return profileUser;}
    public void setUser(String user){profileUser=user;}

	public String getPass(){return profilePass;}
    public void setPass(String pass){profilePass=pass;}

	public String getAddr(){return profileAddr;}
    public void setAddr(String addr){profileAddr=addr;}

	public String getPort(){return profilePort;}
    public void setPort(String port){profilePort=port;}

	public String getShare(){return profileShare;}
    public void setShare(String share){profileShare=share;}

    public String getSmbLevel(){return profileSmbLevel;}
    public void setSmbLevel(String level){profileSmbLevel=level;}

	public boolean isChecked(){return profileIsChecked;}
	public void setChecked(boolean p){profileIsChecked=p;}

	@Override
	public int compareTo(SmbServerConfig o) {
		if(this.profileName != null)
			return this.profileName.toLowerCase().compareTo(o.getName().toLowerCase()) ;
//			return this.filename.toLowerCase().compareTo(o.getName().toLowerCase()) * (-1);
		else
			throw new IllegalArgumentException();
	}
}
