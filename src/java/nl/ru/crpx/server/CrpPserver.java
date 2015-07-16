/*
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nl.ru.crpx.dataobject.DataFormat;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectPlain;
import nl.ru.crpx.project.PrjTypeManager;
import nl.ru.crpx.search.SearchManager;
import nl.ru.crpx.search.SearchParameters;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.server.requesthandlers.RequestHandler;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.crpx.tools.FileIO;
import nl.ru.util.Json;
import nl.ru.util.LogUtil;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author Erwin R. Komen
 */
/* ---------------------------------------------------------------------------
   Name: CrpPserver
   Goal: Main entry point for the CRPP-webserver
   History:
   01/jun/2015   ERK Created for Java
   --------------------------------------------------------------------------- */
@WebServlet(name = "crppw", 
  urlPatterns = {"/debug", "/load", "/save", "/exe", "/statusxq", "/show", 
                "/crpget", "/crpset", "/crpdel", "/update"})
public class CrpPserver extends HttpServlet  {
  // The servlet contains a 'logger'
  private static final Logger logger = Logger.getLogger(CrpPserver.class);
  private static final ErrHandle errHandle = new ErrHandle(CrpPserver.class);
  // =================== instance variables ==================================
  private static JSONObject config;             // Configuration object
  private static SearchManager searchManager;   // The search manager we make
  private static PrjTypeManager prjTypeManager; // 
  private static CrpManager crpManager;         // Link to the CRP-User list manager
  // =================== Simple getters =======================================
  public SearchManager getSearchManager() {return searchManager;}
  public PrjTypeManager getPrjTypeManager() { return prjTypeManager;}
  public JSONObject getConfig() { return config;}
  public CrpManager getCrpManager() { return crpManager; }
  
/* ---------------------------------------------------------------------------
   Name: init
   Goal: Main entry point for the CRPP-webserver
   History:
   01/jun/2015   ERK Created for Java
   --------------------------------------------------------------------------- */
  @Override
  public void init() throws ServletException {
    InputStream is = null;  // Th config file as input stream
    
    // Default init if no log4j.properties are found
    LogUtil.initLog4jIfNotAlready(Level.DEBUG);
    try {
      // Log the start of this server
      logger.info("Starting Crpp Server...");
      // Perform the standard initilization of the servled I extend
      super.init();

      // Perform initialisations related to this project-type using the config file
      // Read it from a package parent
      String configFileName = "crpp-settings.json";
      File configFile = new File(getServletContext().getRealPath("/../../../" + configFileName));
      // One check
      if (!configFile.exists()) {
        configFile = new File(getServletContext().getRealPath("/../../" + configFileName));
        // One more check
        if (!configFile.exists()) {
          configFile = new File(getServletContext().getRealPath("/../" + configFileName));
        }
      }
      // Check if it is there
      if (configFile.exists()) {
        // It exists, so open it up
        is = new BufferedInputStream(new FileInputStream(configFile));
      }
      if (is == null) {
        configFileName = "crpp-settings-default.json.txt";  // Internal default
        // is = FileUtil.getInputStream(configFileName);
        is = FileIO.getProjectDirectory(CrpPserver.class, configFileName);
        if (is == null) {
          // We cannot continue...
          errHandle.DoError("Could not find " + configFileName + "!");
        }
        // Show where we are reading config from
        errHandle.debug("config: " + configFileName + " on project directory");
      } else {
        // Show where we are reading config from
        errHandle.debug("config: " + configFile.getAbsolutePath());
      }
      // Process input stream with configuration
      try {
        try {
          config = Json.read(is);
        } finally {
          if (is != null) is.close();
        }
      } catch (Exception e) {
        errHandle.DoError("Error reading JSON config file: " +  e.getMessage());
      }

      // Create a new search manager
      searchManager = new SearchManager(config);

      // Create a new project type manager
      prjTypeManager = new PrjTypeManager(config);
      
      // Create a new CRP-user list manager
      crpManager = new CrpManager(this, errHandle);

      // Show that we are ready
      logger.info("CrpPserver: server is ready.");
    } catch (Exception ex) {
      errHandle.DoError("CrppS: could not initialize server", ex, CrpPserver.class);
    }
  }

  /* ---------------------------------------------------------------------------
   Name: processRequest
   Goal: Process a request (this may come here from a GET or POST
   Parameters:  @request - HTTP request object
                @responseObject - where to write our response
   History:
   7/nov/2014   ERK Created
   --------------------------------------------------------------------------- */
  //@Override
  protected void processRequest(HttpServletRequest request, HttpServletResponse responseObject) throws UnsupportedEncodingException {
    try {
      // Handle the POST or the GET request in a similar fashion
      boolean debugMode = searchManager.isDebugMode(request.getRemoteAddr());

      // Make sure CORS is allowed (for everyeone)
      responseObject.addHeader("Access-Control-Allow-Origin", "*");
      responseObject.addHeader("Access-Control-Allow-Methods", "POST,GET");

      // Show the number of parallel jobs
      errHandle.debug("Maxparjobs = " + prjTypeManager.getMaxParJobs());

      // Try to formulate a response (call the job-related stuff)
      DataObject response = RequestHandler.handle(this, request);
      
      // Determine response type
      DataFormat outputType = response.getOverrideType(); // some responses override the user's request (i.e. article XML)
      if (outputType == null) {
        // Default output type of the Crpp-server is JSON
        // outputType = DataFormat.JSON;
        outputType = ServletUtil.getOutputType(request, searchManager.getDefaultOutputType());
      }
      
      // Write HTTP headers (content type and cache)
      responseObject.setCharacterEncoding("utf-8");
      responseObject.setContentType(ServletUtil.getContentType(outputType));

      // Continue...
      int cacheTime = response.isCacheAllowed() ? searchManager.getClientCacheTimeSec() : 0;
      ServletUtil.writeCacheHeaders(responseObject, cacheTime);
      
      // Write the response
      OutputStreamWriter out = new OutputStreamWriter(responseObject.getOutputStream(), "utf-8");
      boolean prettyPrint = ServletUtil.getParameter(request, "prettyprint", debugMode);
      String callbackFunction = ServletUtil.getParameter(request, "jsonp", "");
      if (callbackFunction.length() > 0 && !callbackFunction.matches("[_a-zA-Z][_a-zA-Z0-9]+")) {
        response = DataObject.errorObject("JSONP_ILLEGAL_CALLBACK", 
                "Illegal JSONP callback function name. Must be a valid Javascript name.");
        callbackFunction = "";
      }
      String rootEl = "crppsResponse";
      // TODO: handle DataObjectPlain
      if (response instanceof DataObjectPlain && !((DataObjectPlain) response).shouldAddRootElement()) {
        // Plain objects sometimes don't want root objects (e.g. because they're 
        // full XML documents already)
        rootEl = null;
      }
      response.serializeDocument(rootEl, out, outputType, prettyPrint, callbackFunction);
      out.flush();
      
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
      
  }
    /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException, UnsupportedEncodingException {
    try {
      processRequest(request, response);
    } catch (Exception ex) {
      errHandle.DoError("CrppS: doGet failed", ex, CrpPserver.class);
    }
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
          throws ServletException, IOException {
    try {
      processRequest(request, response);
    } catch (UnsupportedEncodingException ex) {
      errHandle.DoError("CrppS: doPost failed", ex, CrpPserver.class);
    }
  }
  
  @Override
  public void destroy() {
    super.destroy();
  }
  /**
   * Provides a short description of this servlet.
   * @return the description
   */
  @Override
  public String getServletInfo() {
    return "Provides CorpusStudioWeb services for the CLARIN-NL project.\n"
            + "Source available at http://github.com/ErwinKomen/\n"
            + "(C) 2015 - ... Meertens Instituut.\n"
            + "Licensed under the Apache License.\n";
  }
  
  /**
   * Get the search-related parameters from the request object.
   *
   * This ignores stuff like the requested output type, etc.
   *
   * Note also that the request type is not part of the SearchParameters, so from looking at these
   * parameters alone, you can't always tell what type of search we're doing. The RequestHandler subclass
   * will add a jobclass parameter when executing the actual search.
   *
   * @param request the kind of action
   * @return the unique key
   */
  public SearchParameters getSearchParameters(String request) {
    try {
      // Set up a parameters object
      SearchParameters param = new SearchParameters(searchManager);
      Properties arProp = System.getProperties();

      // Walk all relevant search parameters
      for (String name: searchManager.getSearchParameterNames()) {
        String value = "";  // Default value
        switch (name) {
          case "resultsType": value = "XML"; break;
          case "waitfortotal": value= "no"; break;
          case "tmpdir": 
            // Create temporary file
            File fTmp = File.createTempFile("tmp", ".txt");
            // Get the path of this file
            String sPath = fTmp.getAbsolutePath();
            value = sPath.substring(0,sPath.lastIndexOf(File.separator));
            // value = Files.createTempDirectory("tmpdir").toString(); // System.getProperty("tmpdir");
        }
        // Check if it has some kind of value
        if (value.length() == 0) continue;
        // Since it really has a value, add it to the parameters object
        param.put(name, value);
      }
      // Return the object that contains the parameters
      return param;
    } catch (Exception ex) {
      errHandle.DoError("could not get search parameters", ex, CrpPserver.class);
      return null;
    }
  }  
  
}
