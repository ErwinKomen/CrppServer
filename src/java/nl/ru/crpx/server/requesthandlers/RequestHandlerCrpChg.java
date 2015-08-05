package nl.ru.crpx.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import nl.ru.util.FileUtil;
import static nl.ru.util.StringUtil.unescapeHexCoding;
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
 * RequestHandlerCrpChg
 *    Receive and store a crp (corpus research project) at the CrpServer
 *      for the indicated user
 * Params:
 *    crp     - name of the crpx (with or without postfix .crpx)
 *    userid  - name of the user under which the .crpx is stored
 *    key     - which 'key' is being changed
 *    value   - new value for this 'key'
 * 
 * @author Erwin R. Komen
 */
public class RequestHandlerCrpChg extends RequestHandler {
  @SuppressWarnings("hiding")
  // =================== Static variables =======================================
  private static final Logger logger = Logger.getLogger(RequestHandlerCrpChg.class);
  // =================== Local variables =======================================
  private CrpManager crpManager;

  // =================== Initialisation of this class ==========================
  public RequestHandlerCrpChg(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
    // Get my local access to the Crp-User list manager
    this.crpManager = servlet.getCrpManager();
  }
  
  @Override
  public DataObject handle() {
    
    try {
      debug(logger, "REQ crpchg");
      // We are expecting a 'multipart' request consisting of two parts:
      //  "query" - a stringified JSON object (see below)
      //  "file"  - a file
      //
      // The JSON object "query" consists of:
      //   {  "userid": "erkomen",
      //      "crp":    "ParticleA.crpx",
      //      "key":    "Goal",
      //      "value":  "This CRP serves as an example" }
      sReqArgument = getReqString(request);
      logger.debug("Considering request /crpchg: " + sReqArgument);
      // Take apart the request object
      JSONObject jReq = new JSONObject(sReqArgument);
      if (!jReq.has("userid")) return DataObject.errorObject("syntax", 
          "The /crpchg request must contain: userid.");
      sCurrentUserId = jReq.getString("userid");
      // Key and value are required
      if (!jReq.has("key")) return DataObject.errorObject("syntax", 
          "The /crpchg request must contain: key.");
      if (!jReq.has("value")) return DataObject.errorObject("syntax", 
          "The /crpchg request must contain: value.");
      // Get the key and value
      String sChgKey = jReq.getString("key");
      String sChgValue = jReq.getString("value");
      // Get the CRP NAME
      if (!jReq.has("crp"))return DataObject.errorObject("syntax", 
          "The /crpchg request must contain: crp (name of the crp).");
      String sCrpName = jReq.getString("crp");
      // Load the correct crp container
      CorpusResearchProject crpChg = crpManager.getCrp(sCrpName, sCurrentUserId);
      // Process the 'value' change in the 'key' within [crpChg]
      boolean bChanged = crpChg.doChange(sChgKey, sChgValue);
      if (bChanged) {
        // Save the changes
        crpChg.Save();
      }
      
      // Content part
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("key", sChgKey);
      objContent.put("value", sChgValue);
      objContent.put("crp", sCrpName);
      objContent.put("changed", bChanged);
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "The value of the CRP's key has been changed at the server");
      objStatus.put("userid", userId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Uploading a CRP failed", ex, RequestHandlerCrpChg.class);
      return null;
    }
  }
}
