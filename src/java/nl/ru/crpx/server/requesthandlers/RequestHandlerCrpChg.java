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

import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.crpx.server.crp.CrpManager;
import static nl.ru.util.StringUtil.decompressSafe;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
import org.apache.log4j.Logger;

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
    boolean bIsList = false;
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
      //      "id":     -1,
      //      "value":  "This CRP serves as an example" }
      // Or: { "userid": "erkomen", "crp": "ParticleA.crpx", 
      //       "list": [{"key": "Goal", "id": -1, "value": ""}, {...}] }
      sReqArgument = getReqString(request);
      logger.debug("Considering request /crpchg: [" + sReqArgument + "]");
      // Convert request object into JSON
      JSONObject jReq = new JSONObject(sReqArgument);
            
      // Validate the parameters
      if (!jReq.has("userid"))  return DataObject.errorObject("syntax", "The /crpchg request must contain: userid.");
      if (!jReq.has("crp"))     return DataObject.errorObject("syntax", "The /crpchg request must contain: crp (name of the crp).");
      // Check for 'key' or 'list'
      if (!jReq.has("key") && jReq.has("list"))
        bIsList = true;
      else {
        if (!jReq.has("key"))   return DataObject.errorObject("syntax", "The /crpchg request must contain: key.");
        if (!jReq.has("value")) return DataObject.errorObject("syntax", "The /crpchg request must contain: value.");
        if (!jReq.has("id"))    return DataObject.errorObject("syntax", "The /crpchg request must contain: id.");
      }
      
      // Retrieve and process the parameters 
      sCurrentUserId = jReq.getString("userid");
      // Get the CRP NAME
      String sCrpName = jReq.getString("crp");

      // Load the correct crp container
      CorpusResearchProject crpChg = crpManager.getCrp(sCrpName, sCurrentUserId);
      // Initialise changed
      boolean bChanged = false;
      DataObjectList dlList = new DataObjectList("list");
      JSONArray arChanges = null;
      
      // List or item?
      if (bIsList) {
        // Get the list: it is escape-coded
        String sList = decompressSafe(jReq.getString("list"));
        // CHeck what we have: is it proper?
        if (!sList.startsWith("[") || !sList.endsWith("]")) {
          // THis is no proper JSON list
          return DataObject.errorObject("syntax", "The /crpchg 'list' parameter does not contain JSON");
        }
        errHandle.debug("List unescaped = [" + sList + "]");
        // Just in case
        try {
          arChanges = new JSONArray(sList);
        } catch (Exception ex) {
          // Return with an appropriate error message
          return DataObject.errorObject("syntax", 
                  "The /crpchg 'list' parameter gives a JSON error: "+ ex.getMessage());
        }
        // Walk all changes
        for (int i=0;i<arChanges.length();i++) {
          JSONObject oItem = arChanges.getJSONObject(i);
          // Get the key, value and id
          String sChgKey = oItem.getString("key");
          // String sChgValue = decompressSafe(oItem.getString("value"));
          String sChgValue = oItem.getString("value");
          int iChgId = oItem.getInt("id");
          errHandle.debug("List item "+i+": ["+sChgKey+"] ["+iChgId+"] ["+sChgValue+"]" );
          // Special case: creation of CRP
          if (sChgKey.equals("create")) {
            // Create the CRP for this user
            crpChg = crpManager.createCrp(sCrpName, sCurrentUserId);
          } else {
            // If there is no known CRP at this point, there is an error
            if (crpChg == null)
              return DataObject.errorObject("availability", "The /crpchg request looks for a CRP that is not there");
            // Process the 'value' change in the 'key' within [crpChg]
            if (crpChg.doChange(sChgKey, sChgValue, iChgId)) bChanged = true;
          }
          // Add a dataobject item
          DataObjectMapElement oMap = new DataObjectMapElement();
          oMap.put("key", sChgKey);
          oMap.put("id", iChgId);
          oMap.put("value", sChgValue);
          dlList.add(oMap);
        }
      } else {
        // Get the key, value and id
        String sChgKey = jReq.getString("key");
        String sChgValue = decompressSafe(jReq.getString("value"));
        int iChgId = jReq.getInt("id");
        // Special case: creation of CRP
        if (sChgKey.equals("create")) {
          // If there is no known CRP at this point, there is an error
          if (crpChg == null)
            return DataObject.errorObject("availability", "The /crpchg request looks for a CRP that is not there");
          // Create the CRP for this user
          crpChg = crpManager.createCrp(sCrpName, sCurrentUserId);
          // crpChg = crpManager.getCrp(sCrpName, sCurrentUserId);
        } else {
          // Process the 'value' change in the 'key' within [crpChg]
          bChanged = crpChg.doChange(sChgKey, sChgValue, iChgId);
        }
      }
      // Save any changes!
      if (bChanged) {
        // Save the changes
        crpChg.Save();
        // Remove the combination CRP/user from the crp manager, so that the fresh CRP will be loaded next time
        crpManager.removeCrpUser(sCrpName, sCurrentUserId);
      }
      
      
      // Content part
      DataObjectMapElement objContent = new DataObjectMapElement();
      objContent.put("crp", sCrpName);
      objContent.put("changed", bChanged);
      if (bIsList) {
        objContent.put("list", dlList);
      } else {
        objContent.put("key", jReq.getString("key"));
        objContent.put("value", jReq.getString("value"));
        objContent.put("id", jReq.getInt("id"));
      }
      // Prepare a status object to return
      DataObjectMapElement objStatus = new DataObjectMapElement();
      objStatus.put("code", "completed");
      objStatus.put("message", "The value of the CRP's key has been changed at the server");
      objStatus.put("userid", sCurrentUserId);
      // Prepare the total response: indexName + status object
      DataObjectMapElement response = new DataObjectMapElement();
      response.put("indexName", indexName);
      response.put("content", objContent);
      response.put("status", objStatus);
      return response;
    } catch (Exception ex) {
      errHandle.DoError("Processing CrpChg failed", ex, RequestHandlerCrpChg.class);
      return null;
    }
  }
}
