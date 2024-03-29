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

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataFormat;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
import nl.ru.util.StringUtil;
import nl.ru.util.json.JSONObject;
//import java.util.logging.Logger;
import java.util.logging.Logger;


/**
 * RequestHandlerDbGet
 *    Copy a research database that is stored at the CrpServer
 * Params:
 *    name    - name of the dbase (with or without postfix .xml)
 *    userid  - name of the user under which the .xml is stored
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerDbGet extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerDbGet.class.getName());
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerDbGet(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    File fDbPath = null;
    String sPart = "";      // Optional corpus part (short subdirectory name)
    DataObjectMapElement objContent = new DataObjectMapElement();

    try {
      debug(logger, "REQ dbget");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen" 
      //      "name":   "ParticleA_dbase.xml"   
      //      "part":   "lModE"                 OPTIONAL: the `dir` of the corpus part
      //      "type":   "xml"                   OR: "csv", "db"
      //    }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      debug(logger, "Considering request /dbget: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      
      // Validate the parameters
      if (!jReq.has("userid"))  return DataObject.errorObject("syntax", "The /dbget request must contain: userid.");
      if (!jReq.has("name"))    return DataObject.errorObject("syntax", "The /dbget request must contain: name.");
      
      // Retrieve and process the obligatory parameters 
      sCurrentUserId = jReq.getString("userid");
      // Get the CRP NAME
      String sDbName = jReq.getString("name");
      if (!sDbName.endsWith(".xml")) sDbName += ".xml";
      
      // Process the optional TYPE parameter
      String sGetType = "xml";
      if (jReq.has("type")) { sGetType = jReq.getString("type");}
      if (jReq.has("part")) { sPart = jReq.getString("part");}
      
      // Get a list of all this user's CRPs satisfying the name condition
      DataObjectList arDbList = (DataObjectList) crpManager.getDbList( sCurrentUserId, sDbName);
      // Check the result
      if (arDbList.isEmpty()) {
        return DataObject.errorObject("not_found", "The .xml file requested is not available.");
      } 
      // Locate the file
      JSONObject oFirst = new JSONObject(arDbList.get(0).toString(DataFormat.JSON));
      String sDbPath = oFirst.getString("file");
      // If a 'part' is specified, it should be included as well
      if (!sPart.isEmpty()) {
        // Reconstruct sDbPath to include the part
        fDbPath = new File(sDbPath);
        sDbPath = Paths.get(fDbPath.getParent(), sPart, fDbPath.getName()).toString();
      }
      // Possibly adapt for which file we are looking
      switch(sGetType) {
        case "csv":
          sDbPath = sDbPath.replace(".xml", ".csv.gz");
          fDbPath = new File(sDbPath);
          if (!fDbPath.exists()) return DataObject.errorObject("not_found",
                  "Could not find the .csv.gz file at: ["+sDbPath+"].");
          // Load and prepare the content
          objContent.put("db", StringUtil.compressSafe(FileUtil.decompressGzipString(sDbPath)));
          break;
        case "xml":
          // if the 'part' is specified, we expect to find .xml.db
          if (sPart.isEmpty()) {
            fDbPath = new File(sDbPath);
            if (!fDbPath.exists()) return DataObject.errorObject("not_found",
                    "Could not find the .xml file at: ["+sDbPath+"].");
            // Load and prepare the content
            objContent.put("db", StringUtil.compressSafe((new FileUtil()).readFile(fDbPath)));
          } else {
            sDbPath = sDbPath.replace(".xml", ".xml.gz");
            fDbPath = new File(sDbPath);
            if (!fDbPath.exists()) return DataObject.errorObject("not_found",
                    "Could not find the .db.gz file at: ["+sDbPath+"].");
            // Read the binary file as a byte array
            byte[] arBytes = Files.readAllBytes(fDbPath.toPath());
            // Load and prepare the content
            objContent.put("db", StringUtil.compressSafe(arBytes));
          }
          break;
        case "db":
          sDbPath = sDbPath.replace(".xml", ".db.gz");
          fDbPath = new File(sDbPath);
          if (!fDbPath.exists()) return DataObject.errorObject("not_found",
                  "Could not find the .db.gz file at: ["+sDbPath+"].");
          // Read the binary file as a byte array
          byte[] arBytes = Files.readAllBytes(fDbPath.toPath());
          // Load and prepare the content
          objContent.put("db", StringUtil.compressSafe(arBytes));
          break;
      }

      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the [db] in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Fetching a result Database failed", ex, RequestHandlerDbGet.class);
      return null;
    }
  }
}
