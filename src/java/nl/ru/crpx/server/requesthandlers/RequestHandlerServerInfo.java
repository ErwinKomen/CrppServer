/*
 * This software has been developed at the "Meertens Instituut"
 *    for the CLARIN project "CorpusStudio-WebApplication".
 *  The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *    while working for the Radboud University Nijmegen.
 *  The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server.requesthandlers;

import java.io.File;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import nl.ru.util.FileUtil;
import org.apache.log4j.Logger;

/**
 *
 * @author Erwin R. Komen
 */
public class RequestHandlerServerInfo extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);

  public RequestHandlerServerInfo(CrpPserver servlet, HttpServletRequest request, String indexName) {
    super(servlet, request, indexName);
  }
  
  @Override
  public DataObject handle() {
    debug(logger, "REQ serverinfo");
    // Get the available language indices through the search manager
    Collection<String> indices = searchMan.getAvailableIndices();
    DataObjectList doIndices = new DataObjectList("index");
    //DataObjectMapAttribute doIndices = new DataObjectMapAttribute("index", "name");
    for (String sIndexName: indices) {
      doIndices.add(sIndexName); //, doIndex);
    }
    // Get the corpus information stored in a file
    String sCorpora = "";
    File fCrpInfo = new File ("/etc/corpora/crp-info.json");
    if (fCrpInfo.exists()) sCorpora = FileUtil.readFile(fCrpInfo);
    
    // Combine all of it
    DataObjectMapElement objContent = new DataObjectMapElement();
    objContent.put("indices", doIndices);
    objContent.put("corpora", sCorpora);
    
    // Prepare a status object to return
    DataObjectMapElement objStatus = new DataObjectMapElement();
    objStatus.put("code", "completed");
    objStatus.put("message", "See the 'indices' information in @indices and the 'corpora' information in @corpora");
    objStatus.put("userid", userId);
    // Prepare the total response: indexName + status object
    DataObjectMapElement response = new DataObjectMapElement();
    response.put("indexName", indexName);
    response.put("contents", objContent);
    response.put("status", objStatus);
    return response;
  }   
}
