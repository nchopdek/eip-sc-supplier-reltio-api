package com.intel.intg.router;

 

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Hashtable;

 

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.xml.bind.DatatypeConverter;

 

 


public class LDAPAuth {
    
    private String ldapURL;
    private String searchBase;
    private String ldaploc;
    
    public LDAPAuth(String ldapURL ,String searchBase, String ldaploc)
    {
        setLdapURL(ldapURL);
        setsearchBase(searchBase);
        setldaploc(ldaploc);
        
    }
    
    public void setLdapURL(String ldapURL) {
        this.ldapURL = ldapURL;
    }
    public void setsearchBase(String searchBase) {
        this.searchBase = searchBase;
    }
    public void setldaploc(String ldaploc) {
        this.ldaploc = ldaploc;
    }
    public boolean authenticate(String user, String pass) {
        return doAuthenticate(user, pass) != null ? true : false;
    }
    
    public boolean authenticate(String authHeader) {
        return doAuthenticate(authHeader) != null ? true : false;
    }
    
    public boolean authenticateAndAuthorize(String user, String pass, String groupName) {
        boolean isAuthorized = false;
        HashMap<String,Object> ctxMap = doAuthenticate(user, pass);
        if (ctxMap != null) {
            isAuthorized = isMemberOf((DirContext)ctxMap.get("ctx"), user, groupName);
        }
        return isAuthorized;
    }
    
    public boolean authenticateAndAuthorize(String authHeader, String groupName) {
        boolean isAuthorized = false;
        HashMap<String,Object> ctxMap =  doAuthenticate(authHeader);
        if (ctxMap != null) {
            isAuthorized = isMemberOf((DirContext)ctxMap.get("ctx"), (String)ctxMap.get("user"), groupName);
        }
        return isAuthorized;
    }
    

 

    private HashMap<String, Object> doAuthenticate(String user, String pass) {
        HashMap<String, Object> ctxMap = getContext(user, pass);
        if (ctxMap == null) {
            System.out.println("User could not be authenticated!");
            
        } 
        return ctxMap;
    }
    
    private HashMap<String, Object> doAuthenticate(String authHeader) {
        HashMap<String, String> params = parseAuthHeader(authHeader);
        return doAuthenticate(params.get("user"), params.get("pwd"));
    }
    
    private HashMap<String,String> parseAuthHeader(String authHeader) {
        String credentials;
        HashMap<String, String> params = new HashMap<String, String>();
        if (authHeader != null && authHeader.startsWith("Basic")) {
            final String base64Credentials = authHeader.substring("Basic".length()).trim();    
            try {
                credentials = new String(DatatypeConverter.parseBase64Binary(base64Credentials),"UTF-8");
            } catch (UnsupportedEncodingException e) {
                
                System.out.println("Unable to decode Authorization header!");
                return params;
            }
        } else {
            
            System.out.println("Authorization header should begin with 'Basic'!");
            return params;
            }
        if (credentials.contains(":") && (credentials.length() > 3)) {
            final String[] values = credentials.split(":", 2);
            params.put("user", values[0]);
            params.put("pwd", values[1]);
            //final String domain = user.substring(0, user.indexOf("\\"));
            return params;
        } 
        return params;
    }
    
    
    
    
    /* Performs authentication against LDAP. 
     * If a DirContext object is created then the credentials are valid
     * */
    private HashMap<String,Object> getContext(String user, String pass) {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapURL);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user);
        env.put(Context.SECURITY_CREDENTIALS, pass);
        env.put(Context.SECURITY_PROTOCOL, "ssl");
        String keystore = "${MULE_JKS_LOC}/MasterTruststore.jks";
        //String keystore = "C:/jks/MasterTruststore.jks";
        System.setProperty("javax.net.ssl.trustStore", keystore);
        DirContext ctx=null;
        try {
            // next line will throw an exception if not able to authenticate the user
            ctx = new InitialDirContext(env);
            HashMap<String,Object> map = new HashMap<String,Object>();
            map.put("ctx", ctx);
            map.put("user", user);
            return map;
        } catch (NamingException e) {}
        return null;
    }

 


    
    private boolean isMemberOf(DirContext ctx, String user, String groupName) {
        boolean MemberFound = false;
        String idsid = user.substring(user.lastIndexOf("\\") + 1);
        StringBuilder searchFilter = new StringBuilder("(&");
        //searchFilter.append("(objectClass=user)");
        searchFilter.append("(sAMAccountName=" + idsid + ")");
        searchFilter.append("(memberOf=CN=" + groupName + ","+ ldaploc + ")");
        searchFilter.append(")");

 

        SearchControls sCtrl = new SearchControls();
        sCtrl.setSearchScope(SearchControls.SUBTREE_SCOPE);
        NamingEnumeration<SearchResult> answer;
        try {
            answer = ctx.search(searchBase, searchFilter.toString(),sCtrl);
        } catch (NamingException e) {
            return false;
        }
        if (answer.hasMoreElements()) {
            MemberFound = true;
        }
        return MemberFound;
    }
}
 