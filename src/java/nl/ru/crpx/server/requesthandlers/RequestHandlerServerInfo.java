/*
 * This software has been developed at the "Meertens Instituut"
 *    for the CLARIN project "CorpusStudio-WebApplication".
 *  The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *    while working for the Radboud University Nijmegen.
 *  The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server.requesthandlers;

import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.server.CrpPserver;
import org.apache.log4j.Logger;

/**
 *
 * @author u459154
 */
public class RequestHandlerServerInfo extends RequestHandler {
  @SuppressWarnings("hiding")
  private static final Logger logger = Logger.getLogger(RequestHandlerDebug.class);

  public RequestHandlerServerInfo(CrpPserver servlet, HttpServletRequest request, String indexName, String urlResource) {
    super(servlet, request, indexName, urlResource);
  }
  
  @Override
  public DataObject handle() {
    debug(logger, "REQ serverinfo");
    Collection<String> indices = searchMan.getAvailableIndices();
    DataObjectList doIndices = new DataObjectList("index");
    //DataObjectMapAttribute doIndices = new DataObjectMapAttribute("index", "name");
    for (String sIndexName: indices) {
      doIndices.add(sIndexName); //, doIndex);
    }
    // Prepare a status object to return
    DataObjectMapElement objStatus = new DataObjectMapElement();
    objStatus.put("code", "completed");
    objStatus.put("message", "Server information follows");
    objStatus.put("userid", userId);
    // Prepare the total response: indexName + status object
    DataObjectMapElement response = new DataObjectMapElement();
    response.put("indexName", indexName);
    response.put("indices", doIndices);
    response.put("status", objStatus);
    return response;
  }   
}
