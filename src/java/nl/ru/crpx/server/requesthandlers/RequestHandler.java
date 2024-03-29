/**
 * Copyright (c) 2015 CLARIN-NL.
 * All rights reserved.
 *
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 * 
 * @author Erwin R. Komen
 */
package nl.ru.crpx.server.requesthandlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.project.CrpInfo;
import nl.ru.crpx.search.Job;
import nl.ru.crpx.search.RunAny;
import nl.ru.crpx.search.SearchManager;
import nl.ru.crpx.search.SearchParameters;
import nl.ru.crpx.search.WorkQueueXqF;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.util.UserFile;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.crpx.tools.General;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONObject;
//import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Base class for the web-service request-handlers that handle the different
 * requests that can be made from the command-line interface.
 * A similar class exists for processing requests by a command-line interface.
 *
 * @author Erwin R. Komen
 */
public abstract class RequestHandler {
  // ============== My own error handler =======================================
  static final ErrHandle errHandle = new ErrHandle(RequestHandler.class);

  // ============== Variables belonging to the request handler ================
  /** The available request handlers by name */
  static Map<String, Class<? extends RequestHandler>> availableHandlers;

  // Fill the map with all the handler classes
  static {
    availableHandlers = new HashMap<String, Class<? extends RequestHandler>>();
    availableHandlers.put("debug", RequestHandlerDebug.class);
  }
  Map<String, String[]> reqParams;
  String userId = "undefined user id";          // Default user id
  String sCurrentUserId = "";                   // Local copy of userid
  static String lastIP = "";                    /** IP address of last request */
  static String sProjectBase = CrpInfo.sEtcProject + "/"; // "/etc/project/"; // Base directory where user-spaces are stored
  static String sCorpusBase = CrpInfo.sEtcCorpora + "/";  // "/etc/corpora/";  // Base directory where corpora are stored
  static List<UserFile> lUserFile = new ArrayList<>();
  /** The work queue to handle XqF jobs */
  WorkQueueXqF workQueue;
  
  // ================== Other instance variables ================================
  String sReqArgument = "";     // Request arguments
  String sLastReqArg = "";      // Arguments of last request
  String sCrpFile = "";         // Full path of the CRP file we are handling
  // String strProject;            // The name of the project 
  String indexName;             // The query specification
  // File indexDir;                // Pointer to the input directory
  SearchParameters searchParam; // Search parameters from request 
  SearchManager searchMan;      // The search manager, which executes and caches our searches 
  CrpPserver servlet;           // The servlet 
  HttpServletRequest request;   // The HTTP request object 
  CorpusResearchProject prjThis;// The corpus research project we are processing 
  // ============== Class initiator ============================================
  RequestHandler(CrpPserver servlet, HttpServletRequest request, String indexName) {
    try {
      // Get the current user/session ID from the system
      // NOTE: this gets the executing user on the server, so is of little use
      this.userId = System.getProperty("user.name");
      // Take over the calling servlet
      this.servlet = servlet;
      this.request  = request;
      // Take over the index name
      this.indexName = indexName;
      // this.indexDir  = null;
      // Get the search manager from the calling CrpxProcessor or CrpPserver
      this.searchMan = servlet.getSearchManager();
      this.searchParam = servlet.getSearchParameters(indexName);
      
      // See if we can get alternatives for project and corpus bases
      JSONObject oConfig = servlet.getConfig();
      if (oConfig.has("projectBase")) {
        sProjectBase = oConfig.getString("projectBase"); 
        errHandle.debug("RequestHandler: projectBase is set to " + sProjectBase);
      }
      if (oConfig.has("corpusBase")) sCorpusBase = oConfig.getString("corpusBase");
      if (oConfig.has("requests")) {
        JSONObject oReq = oConfig.getJSONObject("requests");
        String sOutputType = oReq.getString("defaultOutputType");
        this.searchParam.put("resultsType", sOutputType);
      }
      
      // Initially indicate that no project has been loaded yet
      this.prjThis = null;
    } catch (Exception ex) {
      errHandle.DoError("Could not create [RequestHandler]", ex, RequestHandler.class);
    }
  }
  
  public static String getDbFilename(String sName, String sUserId) {
    try {
      String sProjectPath = sName;
      
      // Set the project path straight
      if (!sProjectPath.contains("/")) {
        sProjectPath = FileUtil.nameNormalize(sProjectBase + "/" + sUserId + "/dbase/"+ sProjectPath);
        if (!sProjectPath.contains(".xml")) {
          sProjectPath += ".xml";
        }
      }
      // Return our findings
      return sProjectPath;
    } catch (Exception ex) {
      errHandle.DoError("Could not get Result Dbase file name", ex, RequestHandler.class);
      return "";
    }
  }
  /**
   * Given the name of a CRP, get its full path
   * 
   * @param sName   name of project without / or \
   * @param sUserId id of the user for this project
   * @return 
   */
  public static String getCrpPath(String sName, String sUserId) {
    try {
      String sProjectPath = sName;
      
      // Set the project path straight
      if (!sProjectPath.contains("/")) {
        sProjectPath = FileUtil.nameNormalize(sProjectBase + "/" + sUserId + "/" + sProjectPath);
        if (!sProjectPath.contains(".crpx")) {
          sProjectPath += ".crpx";
        }
      }
      // Return our findings
      return sProjectPath;
    } catch (Exception ex) {
      errHandle.DoError("Could not get CRP path", ex, RequestHandler.class);
      return "";
    }
  }
  
  /**
   * Find the CRP called 'sName' and provide its save date
   * 
   * @param sName   Name of the project 
   * @param sUserId User for this project
   * @return 
   */
  public String getCrpSaveDate(String sName, String sUserId) {
    try {
      String sProjectPath;
      
      // Check the name we received
      if (sName.contains("/") || sName.contains("\\")) {
        sProjectPath = sName;
      } else {
        // Get the full path
        sProjectPath = getCrpPath(sName, sUserId);
      }
      // turn it into a file
      File fCrp = new File(sProjectPath);
      // Check if it exists
      if (fCrp.exists()) {
        // Retrieve and give back toe file's save date
        return General.getSaveDate(fCrp);
      } else {
        // No file means no save date
        return "";
      }
    } catch (Exception ex) {
      errHandle.DoError("Could not get CRP save date", ex, RequestHandler.class);
      return "";
    }
  }
  
  
  /**
   * Handle a request by dispatching it to the corresponding subclass.
   *
   * @param servlet the servlet object
   * @param request the actual request as passed on by the caller
   * @return the response data
   * @throws java.io.UnsupportedEncodingException
   */
  public static DataObject handle(CrpPserver servlet, HttpServletRequest request) throws UnsupportedEncodingException {
    try {
      // Reset the error handling
      errHandle.clearErr();
      // Initialize the userId as something from the HttpSession
      // (Note: subsequent code may get a better userId from the request object)
      // setUserId(request.getSession().getId());
      // userId = request.getSession().getId();

      // Parse the URL
      String servletPath = request.getServletPath();
      if (servletPath == null)
        servletPath = "";
      if (servletPath.startsWith("/"))
        servletPath = servletPath.substring(1);
      if (servletPath.endsWith("/"))
        servletPath = servletPath.substring(0, servletPath.length() - 1);
      String[] parts = servletPath.split("/", 3);
      // The 'indexName' is a combination of: language [subdir [subdir [... [filename ] ] ] ]
      //     the sub directories need to be separated by the ; (semicolon)
      String indexName = parts.length >= 1 ? parts[0] : "";
      // The 'urlResource' part is the actual commend, e.g: exe, statusxq etc
      String urlResource = parts.length >= 2 ? parts[1] : "";
      String urlPathInfo = parts.length >= 3 ? parts[2] : "";

      // Debugging: show the IP + time (EK: but only if this is no repetition)
      String thisIP = request.getRemoteAddr();
      if (!thisIP.equals(lastIP)) {
        lastIP = thisIP;
        errHandle.debug("IP: " + thisIP + " at: " + getCurrentTimeStamp());
      }
      // errHandle.debug("REQ calling for: " + indexName);
      // Choose the RequestHandler subclass
      RequestHandler requestHandler = null;
      switch (indexName) {
        case "crpchg":    // Remove a CRP on the /crpp server side
          requestHandler = new RequestHandlerCrpChg(servlet, request, indexName);
          break;
        case "crpdel":    // Remove a CRP on the /crpp server side
          requestHandler = new RequestHandlerCrpDel(servlet, request, indexName);
          break;
        case "crpget":    // Send CRP from /crpp to requester
          requestHandler = new RequestHandlerCrpGet(servlet, request, indexName);
          break;
        case "crpinfo":   // Send CRP from /crpp to requester
          requestHandler = new RequestHandlerCrpInfo(servlet, request, indexName);
          break;
        case "crplist":   // List available CRPs for one or all user(s)
          requestHandler = new RequestHandlerCrpList(servlet, request, indexName);
          break;
        case "crpset":    // Requester sends a CRP to the /crpp
          requestHandler = new RequestHandlerCrpSet(servlet, request, indexName);
          break;
        case "dbget":     // Send Database from /crpp to requester
          requestHandler = new RequestHandlerDbGet(servlet, request, indexName);
          break;
        case "dbinfo":     // Send Database from /crpp to requester
          requestHandler = new RequestHandlerDbInfo(servlet, request, indexName);
          break;
        case "dblist":    // List available databases for one or all user(s)
          requestHandler = new RequestHandlerDbList(servlet, request, indexName);
          break;
        case "dbset":     // Upload/set one whole database
          requestHandler = new RequestHandlerDbSet(servlet, request, indexName);
          break;
        case "dbupload":  // Upload one part of a database
          requestHandler = new RequestHandlerDbUpload(servlet, request, indexName);
          break;
        case "debug":
          requestHandler = new RequestHandlerDebug(servlet, request, indexName);
          break;
        case "execute": case "exe": // Uitvoeren van een CRP
          requestHandler = new RequestHandlerExecute(servlet, request, indexName);
          break;
        case "load":    // Laden van een CRP voor een gebruiker
          requestHandler = new RequestHandlerLoad(servlet, request, indexName);
          break;
        case "reset":   // Stoppen van XqJob en alle onderliggende XqF jobs
          requestHandler = new RequestHandlerReset(servlet, request, indexName);
          break;
        case "save":    // Opslaan van een CRP voor een gebruiker
          requestHandler = new RequestHandlerSave(servlet, request, indexName);
          break;
        case "serverinfo":  // Information about corpora
          requestHandler = new RequestHandlerServerInfo(servlet, request, indexName);
          break;
        case "settings":    // User settings
          requestHandler = new RequestHandlerSettings(servlet, request, indexName);
          break;
        case "show":    // A summary of the details of the indicated CRP
          requestHandler = new RequestHandlerShow(servlet, request, indexName);
          break;
        case "statusxq":  // Opvragen status XqJob
          requestHandler = new RequestHandlerStatusXq(servlet, request, indexName);
          break;
        case "statusxl":  // Opvragen status RunTxtList job
          requestHandler = new RequestHandlerStatusXl(servlet, request, indexName);
          break;
        case "txt":       // Get the surface text of one particular text
          requestHandler = new RequestHandlerTxt(servlet, request, indexName);
          break;
        case "txtlist":    // List available texts for a corpus (part)
          requestHandler = new RequestHandlerTxtList(servlet, request, indexName);
          break;
        case "update":      // getting detailed information
          requestHandler = new RequestHandlerUpdate(servlet, request, indexName);
          break;
        case "":
          // Empty index name means request for information
          indexName = "serverinfo";
          requestHandler = new RequestHandlerServerInfo(servlet, request, indexName);
          break;
      }

      // Make sure we catch empty requesthandlers
      if (requestHandler == null)
        return DataObject.errorObject("INTERNAL_ERROR", "RequestHandler is empty. Use: /execute, /show, /statusxq");

      // Handle the request
      try {
        return requestHandler.handle();
      } catch (InterruptedException e) {
        return DataObject.errorObject("INTERNAL_ERROR", internalErrorMessage(e, false, 8));
      }
      
    } catch (RuntimeException ex) {
      errHandle.DoError("Handle error", ex, RequestHandler.class);
      return null;
    }
  }
  
  /**
   * Get the user id.
   *
   * Used for logging and making sure 1 user doesn't run too many queries.
   *
   * Right now, we simply use the session id, or the value sent in a parameter.
   *
   * @return the unique user id
   */
  public String getUserId() { return userId; }
  public void setUserId(String sNewId) { userId = sNewId; }
  public String getProjectBase() { return sProjectBase; }
  public String getCorpusBase() { return sCorpusBase;}

  /**
   * getReqString - Read the HTTP servlet request string,
   *                decode into key/value pairs,
   *                process each pair that can be processed,
   *                and return the part that has no key (JSON string)
   * 
   * @param request
   * @return 
   */
  public static String getReqString(HttpServletRequest request) {
    JSONObject oReq;
    String sJsonPart = "";
    
    try {
      // Check if this is a json
      String sContentType = request.getHeader("content-type");
      if (sContentType != null && sContentType.equals("application/json")) {
        // Read the request as a string
        StringBuilder jb = new StringBuilder();
        String line = null;
        try {
          BufferedReader reader = request.getReader();
          while ( (line=reader.readLine()) != null) {
            jb.append(line);
          }
        } catch (Exception e) {
          
        }
        sJsonPart = jb.toString();
      } else {
      
        // Read the request and divide it into key-value pairs
        oReq = getReqObject(request);
        // Check for "query"
        if (oReq.has("query")) {
          // sJsonPart = oReq.getJSONObject("query").toString();
          sJsonPart = oReq.getString("query");
        } else {
          sJsonPart = oReq.toString();
        }
      }
      
      // Post-fixing: translate "True" and "False"
      sJsonPart = sJsonPart.replace("\"True\"", "true");
      sJsonPart = sJsonPart.replace("\"False\"", "false");
      
      // Return the JSON query part
      return sJsonPart;
    } catch (Exception ex) {
      errHandle.DoError("Could not get the search string", ex, RequestHandler.class);
      return "";
    }
  }
  
  /**
   * getReqObject - read the request and transform it into a key/value dataobject
   *                The value that has no key gets the key "query" assigned
   * 
   * @param request - The http servlet request object
   * @return        - A dataobject with a key-value pair listing
   */
  public static JSONObject getReqObject(HttpServletRequest request) {
    JSONObject oBack = new JSONObject();
    String sReqString = "";
    String sMethod = "GET";
    try {
      // Get the query string
      String sQueryIdArg = request.getQueryString();
      // errHandle.debug("getReqObject #1: ["+ sQueryIdArg + "]");
      if (sQueryIdArg == null || sQueryIdArg.isEmpty()) {
        // Perhaps this is a POST request? Try to get POST parameter
        sQueryIdArg = request.getParameter("args"); 
        sMethod = "POST";
      } else if (sQueryIdArg.startsWith("{")) {
        // It is GET, but it is a JSON string, URL-encoded - decode it
        sQueryIdArg = URLDecoder.decode(sQueryIdArg, "UTF-8");
        oBack = new JSONObject(sQueryIdArg);
        return oBack;
      }
      // ============= Debugging ==================
      // errHandle.debug("getReqObject #2: ["+ sMethod + "] string = [" + sQueryIdArg + "]");
      // Perhaps this already is a JSONObject??
      
      // Check for parameters
      // errHandle.debug("There are parameters: " + request.getParameterMap().size() );
      for (String sName : request.getParameterMap().keySet()) {
        String sValue = request.getParameter(sName);
        // =========== Debugging =======
        // errHandle.debug("key = [" + sName + "] value = [" + sValue + "]");
        // =============================
        // Check for empty value
        if (sValue.isEmpty()) {
          sValue = sName;
          sName = "query";
        }
        if (!sValue.isEmpty()) {
          if (sValue.length() > 100)
            errHandle.debug("Parameter [" + sName + "]=[" + sValue.substring(0, 100) + "...]");  
          else
            errHandle.debug("Parameter [" + sName + "]=[" + sValue + "]");  
        } else {
          errHandle.debug("empty value...");
        }
        oBack.put(sName, sValue);
      }
      // If we have a good object, return now
      if (oBack.length()>0) return oBack;
      
      // ==========================================
      if (sQueryIdArg == null) {
        sReqString = "";
      } else {
        sReqString = URLDecoder.decode(sQueryIdArg, "UTF-8");
      }
      // Divide the string into pairs
      String[] pairs = sReqString.split("&");
      // Walk through all pairs
      for (String pair : pairs) {
        // Get a possible equal sign
        int idx = pair.indexOf("=");
        // Do we have an equal sign for this pair?
        if (idx <= 0) {
          // oBack.put("query", new JSONObject(pair));
          oBack.put("query", pair);
        } else {
          // Get the "key" part
          oBack.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
      }
      // Return what we found
      return oBack;
    } catch (UnsupportedEncodingException ex) {
      errHandle.DoError("Could not get the search string", ex, RequestHandler.class);
      return null;
    }
}
  
  /**
   * Get the correct user id.
   *
   * This returns the userid set by the caller or else our own userid. (Our own
   * user id is the session id.)
   *
   * @return the unique user id
   */
  public String getAdaptedUserId(String sReq) {
    // Convert the request string into a json object
    JSONObject jReq = new JSONObject(sReq);
    // Check if there is a "userid" string
    if (jReq.has("userid")) {
      // Return the caller's userid
      return jReq.getString("userid");
    } else {
      // Return the user id we have over here
      return userId;
    }
  }
  
  /**
   * Return the start of the user id.
   *
   * Useful for logging; should be enough for unique identification.
   *
   * @return the unique session id
   */
  public String shortUserId() {
    String sId = getUserId();
    if (sId.length()<6) 
      return sId;
    else
      return sId.substring(0, 6);
  }
  /**
   * Child classes should override this to handle the request.
   * @return the response object
   * @throws java.lang.InterruptedException
   */
  public abstract DataObject handle() throws InterruptedException;
  
  public static String getCurrentTimeStamp() {
    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
    Date now = new Date();
    String strDate = sdfDate.format(now);
    return strDate;
  }

  public static String internalErrorMessage(Exception e, boolean debugMode, int code) {
    if (debugMode) {
      return e.getClass().getName() + ": " + e.getMessage() + " (Internal error code " + code + ")";
    }
    return "An internal error occurred. Please contact the administrator.  Error code: " + code + ".";
  }
  
  public void debug(Logger logger, String msg) {
    // logger.debug(shortUserId() + " " + msg);
    logger.log(Level.INFO, shortUserId() + " " + msg);
  }
  
  /**
   * errorCollect -- combine the errors for a particular job into one message string
   * 
   * @param search
   * @return 
   */
  public String errorCollect(Job search) {
    String sResult = "";    // Collection of errors
    
    try {
      // Set the error message
      if (errHandle.hasErr())
        sResult = errHandle.getErrList().toString() + "\n";
      // Get the list of XQ errors
      List<JSONObject> arErr = search.getJobErrors();
      if (arErr != null && arErr.size() > 0)
        for (int i=0;i<arErr.size();i++)
          sResult += arErr.get(i).toString() + "\n";
      String sJobRes = search.getJobResult();
      // Errors might also be in JobResult...
      if (!sJobRes.isEmpty()) sResult += sJobRes + "\n";
      // Return the combined errors
      return sResult;
    } catch (Exception ex) {
      errHandle.DoError("Problem in errorCollect()", ex, RequestHandler.class);
      return sResult;
    }
  }
  public String errorCollect(RunAny search) {
    String sResult = "";    // Collection of errors
    
    try {
      // Set the error message
      if (errHandle.hasErr())
        sResult = errHandle.getErrList().toString() + "\n";
      // Get the list of XQ errors
      List<JSONObject> arErr = search.getJobErrors();
      if (arErr != null && arErr.size() > 0)
        for (int i=0;i<arErr.size();i++)
          sResult += arErr.get(i).toString() + "\n";
      String sJobRes = search.getJobResult();
      // Errors might also be in JobResult...
      if (!sJobRes.isEmpty()) sResult += sJobRes + "\n";
      // Return the combined errors
      return sResult;
    } catch (Exception ex) {
      errHandle.DoError("Problem in errorCollect()", ex, RequestHandler.class);
      return sResult;
    }
  }

  /**
   * getUserFile  -- Retrieve or create a UserFile object
   * 
   * @param sUserId   -- User responsible for uploading this file
   * @param sFilename -- Name of this file
   * @param iTotal    -- Total number of chunks for this file
   * @param oErr      -- ErrHandle object
   * @return 
   */
  public UserFile getUserFile(String sUserId, String sFilename, int iTotal, ErrHandle oErr) {
    UserFile oThis = null;
    try {
      // Walk the list
      for (int i=0;i<lUserFile.size();i++) {
        if (lUserFile.get(i).name.equals(sFilename)) {
          // Found it!
          oThis = lUserFile.get(i);
          return oThis;
        }
      }
      // Haven't found it: add it
      oThis = new UserFile(sUserId, sFilename, iTotal, oErr);
      synchronized(lUserFile) {
        lUserFile.add(oThis);
      }
      // Return what we found
      return oThis;
    } catch (Exception ex) {
      errHandle.DoError("getUserFile: could not complete", ex);
      return null;
    }
  }
}
