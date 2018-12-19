/*
 * This software has been developed at the "Radboud University Nijmegen"
 *    for the CLARIA-H project "ACAD".
 *  The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import static javax.imageio.ImageIO.read;
import nl.ru.crpx.tools.ErrHandle;

/**
 *
 * @author Erwin R. Komen
 */
public class CrpMserver {
  private static int iBacklog = 40;  // Maximum number of queued incoming connections to allow on the listening socket
  private static int iPort = 6777;   // Port on which to publish = C+M (Crp + Mini)
  // The servlet contains a 'logger'
  private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CrpPserver.class);
  private static final ErrHandle errHandle = new ErrHandle(CrpPserver.class);
  private static final int maxThreadsPerUser = 20;
  // List all the stuff we may choose
  private String[] urlPatterns = 
    {"/crpchg", "/crpdel", "/crpget", "/crpinfo", "/crpset", "/dbinfo", 
     "/dblist", "/dbset", "/dbupload", "/debug", "/exe", 
     "/load", "/save", "/settings", "/show", "/statusxl", "/statusxq", 
     "/txt", "/txtlist", "/update"};
  
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    
    try {
      // Initialise a server on a particular address
      HttpServer server = HttpServer.create(new InetSocketAddress(iPort),iBacklog);
      
      // Bind the different urlPatterns to different handlers
      server.createContext("/applications/myapp", new CrpmHandler());
      server.setExecutor(null); // creates a default executor
      
      // Now start the server
      server.start();
    } catch (IOException ex) {
      errHandle.DoError("CrpM server main", ex, CrpMserver.class);
    }
  }
  
}

class CrpmHandler implements HttpHandler {
  public void handle(HttpExchange t) throws IOException {
    InputStream is = t.getRequestBody();
    read(is); // .. read the request body
    String response = "This is the response (Erwin, RU)";
    t.sendResponseHeaders(200, response.length());
    OutputStream os = t.getResponseBody();
    os.write(response.getBytes());
    os.close();
  }
}
