/**
 * Copyright (c) 2015 CLARIN-NL, (c) 2016 Radboud University Nijmegen
 * All rights reserved.
 *
 * This software has been developed at the "Meertens Instituut"
 *   for the CLARIN project "CorpusStudio-WebApplication".
 *   Additions have been made in 2016 while working at the Radboud University Nijmegen
 * The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *   while working for the Radboud University Nijmegen.
 * The program and the source can be freely used and re-distributed.
 * 
 * @author Erwin R. Komen
 */
package nl.ru.crpx.server.requesthandlers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.crpx.server.util.UserFile;
import static nl.ru.util.StringUtil.decompressSafe;
import nl.ru.util.json.JSONObject;
import java.util.logging.Logger;


/**
 * RequestHandlerDbUpload
 *    Receive and store a Result Database at the CrpServer
 *      for the indicated user
 * Params:
 *    name    - name of the xml Result Dabase (with or without postfix .xml)
 *    userid  - name of the user under which the dbase is stored
 *    crp     - the xml text of the dbase file
 *    overwrite - boolean indicating that we may overwrite an existing copy
 * Optional:
 *    lng     - which language to link to the dbase
 *    dir     - which 'dir' within the language to link to it
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerDbUpload extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDbUpload.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerDbUpload(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    JSONObject jReq;
    boolean bOverwrite = true;
    String sDbText = "";
    String sLng = "";           // Optional "language" (corpus)
    String sDir = "";           // Optional "part" within the language
    
    try {
      debug(logger, "REQ dbupload");
      // The JSON object "query" consists of:
      //   {  "userid":   "erkomen",
      //      "chunk":    3,                      // value '0' means: confirm ready to receive + make room
      //      "total":    14,                     
      //      "dbchunk":  "abkerj/kdr#kje;ar",    // coded
      //      "name":     "ParticleA_Dbase.xml",
      //      "action":   "send",                 // Possible: {init, send, status, stop}
      //      "overwrite": true}
      
      // Check if we have a multi-part upload
      if (this.isMultiPart(request)) {
        // Read the parameters
        jReq = new JSONObject(request.getParameter("args"));
        // Read the file to be uploaded
        Part oPart = request.getPart("fileUpload");
        String sDbChunk = getFileText(oPart);
        sDbText = decompressSafe(sDbChunk);  // The text of the database chunk
      } else {
        // debug(logger, "dbupload = arguments");
        sReqArgument = getReqString(request);
        // Take apart the request object
        jReq = new JSONObject(sReqArgument);
        // Check if this is start
        if (!jReq.has("start") || !jReq.getBoolean("start")) {
          // The chunk is part of the parameters
          if (!jReq.has("dbchunk")) return DataObject.errorObject("syntax", "The /dbupload request must contain: dbchunk.");
          sDbText = decompressSafe(jReq.getString("dbchunk"));  // The text of the database chunk
        }
      }      
      
      // Verify that required parts are here
      if (!jReq.has("userid"))  return DataObject.errorObject("syntax", "The /dbupload request must contain: userid.");
      if (!jReq.has("chunk"))   return DataObject.errorObject("syntax", "The /dbupload request must contain: chunk.");
      if (!jReq.has("total"))   return DataObject.errorObject("syntax", "The /dbupload request must contain: total.");
      if (!jReq.has("name"))    return DataObject.errorObject("syntax", "The /dbupload request must contain: name.");
      if (!jReq.has("action"))  return DataObject.errorObject("syntax", "The /dbupload request must contain: action.");
      
      // Get the values of the required parameters
      sCurrentUserId = jReq.getString("userid");            // The user
      String sDbName = jReq.getString("name");              // Name of the complete file
      int iChunk = jReq.getInt("chunk");                    // Number of this chunk
      int iTotal = jReq.getInt("total");                    // Total number of chunks expected
      String sAction = jReq.getString("action");            // Whether this is the first or not
      
      // Optional parameters: "overwrite"
      if (jReq.has("overwrite")) bOverwrite = jReq.getBoolean("overwrite");
      // Optional parameters: "lng" and "dir"
      if (jReq.has("lng")) sLng = jReq.getString("lng");
      if (jReq.has("dir")) sDir = jReq.getString("dir");
      
      debug(logger, "Considering request /dbupload: [userid="+sCurrentUserId+
              ", action="+sAction+", overwrite="+bOverwrite+
              ", name="+sDbName+", chunk="+iChunk+", total="+iTotal+"]");
     
      // Check if this has the .xml ending
      if (!sDbName.endsWith(".xml")) sDbName += ".xml";
      
      // Content part
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("name", sDbName);
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();

      // Prepare code
      String sCode = "error";
      String sMsg = "(no message)";
      
      // Possibly check existence
      boolean bCheckedExists = false;
      if (!bOverwrite) {
        bCheckedExists = (crpManager.getDbList( sCurrentUserId, sDbName) != null);
      }
      
      // Overwrite protection: continue if we may overwrite, or else, if the db does not exist yet
      if (bOverwrite || !bCheckedExists) {
        // We may continue: get the UserFile object
        UserFile oUserFile = this.getUserFile(sCurrentUserId, sDbName, iTotal, errHandle);
        // If this is the *START*, then we need to clear the list of files
        switch(sAction) {
          case "init":
            // Clear the list of files
            oUserFile.Init();
            // Give an appropriate response
            sCode = "initialized";
            sMsg = "The /crpp server is ready to receive a results database";
            break;
          case "stop":
            // Indicate that we need to STOP
            oUserFile.Stop();
            sCode = "stopped";
            sMsg = "The /crpp server has stopped receiving a results database";
            break;
          case "send":
            // Double check before continuing
            if (oUserFile.interrupt) {
              sCode = "stopping"; sMsg = "Received stop signal";
            } else {
              // Add the chunk at the appropriate location (do NOT send it yet)
              oUserFile.AddChunk(sDbText, iChunk, iTotal);
              // Check if we have all the chunks that are expected
              if (oUserFile.IsReady()) {
                // =========== DEBUG ===================
                errHandle.debug("dbupload - finalizing");
                // =====================================
                // Save the Result Dbase to an appropriate location
                String sResDbase = RequestHandler.getDbFilename(sDbName, sCurrentUserId);
                // We are ready, so combine the fragments
                oUserFile.Write(sResDbase);
                // =========== DEBUG ===================
                errHandle.debug("dbupload written to: "+sResDbase);
                // =====================================
                // Return correct information
                sCode = "completed";
                sMsg = "The result dbase has been stored at the server: "+sResDbase;
                // Clear the list
                oUserFile.Clear();
              } else {
                // =========== DEBUG ===================
                errHandle.debug("dbupload chunk="+ iChunk+" progress="+oUserFile.chunk.size()+ "/"+oUserFile.total);
                // =====================================

                // We are not ready, so return a progress message
                sCode = "working";
                // Return progress information
                objContent.put("read", oUserFile.chunk.size() );
                objContent.put("total", oUserFile.total);
              }
            }
            break;
          case "status":
            // Provide information as to where we are
            break;
        }
        // Double check on status
        if (oUserFile.interrupt) {
          sCode = "stopped";
          sMsg = "The /crpp server has stopped receiving a results database";
        }
      } else {
        // This may hold code to give a better reaction against overwriting an existing database
      }
      
      
      objStatus.put("code", sCode);
      objStatus.put("message", sMsg);
      objStatus.put("userid", sCurrentUserId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Uploading a Result Dbase failed", ex, RequestHandlerDbUpload.class);
      return null;
    }
  }
  
  /**
   * getFileText -- Convert the part to a string
   * 
   * @param oPart
   * @return 
   */
  private String getFileText(Part oPart) {
    try {
      StringBuilder value = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(
              oPart.getInputStream(), "UTF-8"))) {
        char[] buffer = new char[1024];
        for (int length = 0; (length = reader.read(buffer)) > 0;) {
          value.append(buffer, 0, length);
        }
      }
      return value.toString();
    } catch (Exception ex) {
      errHandle.DoError("getFileText", ex, RequestHandlerDbUpload.class);
      return "";
    }
  }
  
  /**
   * isMultiPart -- Check if a Http request is multi-part or not
   * 
   * @param request
   * @return 
   */
  public boolean isMultiPart(HttpServletRequest request) {
    try {
      // Check if we have a multi-part upload
      return (request.getContentType() != null && 
              request.getContentType().toLowerCase().contains("multipart/form-data") );
    } catch (Exception ex) {
      errHandle.DoError("isMultiPart:", ex);
      return false;
    }
  }
}
