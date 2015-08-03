package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataFormat;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
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
 * RequestHandlerCrpDel
 *    Remove a crp (corpus research project) that is stored at the CrpServer
 * Params:
 *    name    - name of the crpx (with or without postfix .crpx)
 *    userid  - name of the user under which the .crpx is stored
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerCrpDel extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerCrpDel.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerCrpDel(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    try {
      debug(logger, "REQ crpdel");
      // Get the JSON string argument we need to process, e.g:
      //   {  "userid": "erkomen" 
      //      "name":   "ParticleA.crpx" }
      // Note: if no user is given, then we should give all users and all crp's
      sReqArgument = getReqString(request);
      logger.debug("Considering request /crpdel: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      if (!jReq.has("userid")) return DataObject.errorObject("syntax", 
          "The /crpdel request must contain: userid.");
      sCurrentUserId = jReq.getString("userid");
      // Check for the name of the crpx
      String sCrpName = "";
      if (!jReq.has("name")) return DataObject.errorObject("syntax", 
          "The /crpdel request must contain: name.");
      sCrpName = jReq.getString("name");
      // Check if this has the .crpx ending
      if (!sCrpName.endsWith(".crpx")) sCrpName += ".crpx";
      // Get a list of all this user's CRPs satisfying the name condition
      DataObjectList arCrpList = (DataObjectList) crpManager.getCrpList( sCurrentUserId, "", sCrpName);
      // Check the result
      if (arCrpList.isEmpty()) {
        return DataObject.errorObject("not_found", 
          "The .crpx file requested is not available.");
      } 
      // Locate the file
      JSONObject oFirst = new JSONObject(arCrpList.get(0).toString(DataFormat.JSON));
      String sCrpPath = oFirst.getString("file");
      File fCrpPath = new File(sCrpPath);
      if (!fCrpPath.exists()) return DataObject.errorObject("not_found",
              "Could not find the .crpx file at: ["+sCrpPath+"].");

      // Remove the file from the server
      fCrpPath.delete();
      
      // Also remove the CRP from the CrpManager
      crpManager.removeCrpUser(sProjectBase, userId);
      
      // Prepare the content: full file name
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("file", sCrpPath);
      objContent.put("name", jReq.getString("name"));
      
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "See the [crp] in the [content] section");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Removing a CRP failed", ex, RequestHandlerCrpDel.class);
      return null;
    }
  }
}
