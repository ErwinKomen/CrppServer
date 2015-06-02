package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.search.SearchManager;
import nl.ru.crpx.search.SearchParameters;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;

/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */

/**
 * Base class for the web-service request-handlers that handle the different
 * requests that can be made from the command-line interface.
 * A similar class exists for processing requests by a command-line interface.
 *
 * @author Erwin R. Komen
 */
public abstract class RequestHandler {
  // ============== My own error handler =======================================
  static final ErrHandle errHandle = new ErrHandle(nl.ru.crpx.cmd.RequestHandler.class);

  // ============== Variables belonging to the request handler ================
  /** The available request handlers by name */
  static Map<String, Class<? extends RequestHandler>> availableHandlers;

  // Fill the map with all the handler classes
  static {
    availableHandlers = new HashMap<String, Class<? extends RequestHandler>>();
    availableHandlers.put("debug", RequestHandlerDebug.class);
  }
  String userId = "undefined user id";          // Default user id
  String sCurrentUserId = "";                   // Local copy of userid
  static String lastIP = "";                    /** IP address of last request */
  static String sProjectBase = "/etc/project/"; // Base directory where user-spaces are stored
  static String sCorpusBase = "/etc/corpora/";  // Base directory where corpora are stored
  // ================== Other instance variables ================================
  String sReqArgument = "";     // Request arguments
  String sLastReqArg = "";      // Arguments of last request
  String sCrpFile = "";         // Full path of the CRP file we are handling
  String strProject;            // The name of the project 
  String indexName;             // The query specification
  File indexDir;                // Pointer to the input directory
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
      this.indexDir  = null;
      // Get the search manager from the calling CrpxProcessor
      this.searchMan = servlet.getSearchManager();
      this.searchParam = servlet.getSearchParameters(indexName);
      
      // See if we can get alternatives for project and corpus bases
      JSONObject oConfig = servlet.getConfig();
      if (oConfig.has("projectBase")) sProjectBase = oConfig.getString("projectBase");
      if (oConfig.has("corpusBase")) sCorpusBase = oConfig.getString("corpusBase");
      
      // Initially indicate that no project has been loaded yet
      this.prjThis = null;
      /* 
      // Set up a Request Argument JSON string, mimicking server processing
      sReqArgument = "{ \"userid\": \"" + userId + "\", " + 
              "\"query\": \"" + strProject + "\"}";
      */
    } catch (Exception ex) {
      errHandle.DoError("Could not create [RequestHandler]", ex, RequestHandler.class);
    }
  }
  
  /* ---------------------------------------------------------------------------
   Name: initCrp
   Goal: Initialize CRP-related parameters for this requesthandler
   Parameters:  @request - HTTP request object
                @responseObject - where to write our response
   History:
   7/nov/2014   ERK Created
   --------------------------------------------------------------------------- */
  public boolean initCrp(String sProjectPath, String sLngIndex) {
    String sInputDir;   // Input directory for this project
    String sOutputDir;  // Output directory for this project
    String sQueryDir;   // Query directory for this project

    try {
      // Get the correct input directory from the [indexName] part
      this.indexDir = servlet.getSearchManager().getIndexDir(sLngIndex);
      // Create room for a corpus research project
      CorpusResearchProject crpThis = new CorpusResearchProject();
      // Set output and query directory, depending on the user
      sOutputDir = FileUtil.nameNormalize(sProjectBase + "/" + this.userId + "/out");
      sQueryDir = FileUtil.nameNormalize(sProjectBase + "/" + this.userId + "/xq");
      sInputDir = this.indexDir.getAbsolutePath();
      // Set the project path straight
      if (!sProjectPath.contains("/")) {
        sProjectPath = FileUtil.nameNormalize(sProjectBase + "/" + this.userId + "/" + sProjectPath);
        if (!sProjectPath.contains(".")) {
          sProjectPath += ".crpx";
        }
      }
      // Load the project
      if (!crpThis.Load(sProjectPath, sInputDir, sOutputDir, sQueryDir)) {
        errHandle.DoError("Could not load project " + strProject);
        // Try to show the list of errors, if there is one
        String sMsg = crpThis.errHandle.getErrList().toString();
        errHandle.DoError("List of errors:\n" + sMsg);
        return false;
      }

      // Modify the input directory
      sInputDir = sCorpusBase;
      

      // Get my copy of the project
      this.prjThis = crpThis;
      // Add the project name as parameter
      this.searchParam.put("query", crpThis.getName());
      // Set the project type manager for the CRP
      crpThis.setPrjTypeManager(servlet.getPrjTypeManager());
      crpThis.setSearchManager(this.searchMan);
      // Get the name of the project
      this.strProject = crpThis.getName();
      // Return positively
      return true;
    } catch (Exception ex) {
      errHandle.DoError("There's a problem initializaing the CRP", ex, RequestHandler.class);
      // Return failure
      return false;
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

      // Choose the RequestHandler subclass
      RequestHandler requestHandler = null;
      switch (indexName) {
        case "debug":
          requestHandler = new RequestHandlerDebug(servlet, request, indexName);
          break;
        case "show":
          requestHandler = new RequestHandlerShow(servlet, request, indexName);
          break;
        case "execute": case "exe":
          requestHandler = new RequestHandlerExecute(servlet, request, indexName);
          break;
        case "statusxq":
          requestHandler = new RequestHandlerStatusXq(servlet, request, indexName);
          break;
        case "":
          // Empty index name means request for information
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
  public String getUserId() {
    return userId;
  }

  public void setUserId(String sNewId) {
    userId = sNewId;
  }

  public static String getReqString(HttpServletRequest request) {
    String sReqString = "";
    try {
      // Get the query string
      String sQueryIdArg = request.getQueryString();
      if (sQueryIdArg == null || sQueryIdArg.isEmpty()) {
        // Perhaps this is a POST request? Try to get POST parameter
        sQueryIdArg = request.getParameter("args");        
      }
      if (sQueryIdArg == null) {
        sReqString = "";
      } else {
        sReqString = URLDecoder.decode(sQueryIdArg, "UTF-8");
      }
    } catch (UnsupportedEncodingException ex) {
      errHandle.DoError("Could not get the search string", ex, RequestHandler.class);
    }
    // Return the result
    return(sReqString);
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
    logger.debug(shortUserId() + " " + msg);
  }


}
