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
package nl.ru.crpx.server.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.Part;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.FileUtil;

/**
 * UserFile -- Holds all information pertaining to a file that is
 *             being uploaded in chunks
 * 
 * NOTE:       This version of UserFile differs from the one in nl.ru.crpstudio.util
 * @author Erwin
 */
public class UserFile {
  // ========================= Constants =======================================
  static String sProjectBase = "/etc/project/"; // Base directory where user-spaces are stored
  // ================ Private variables ==============
  private ErrHandle errHandle;
  // ================ Public variables ===============
  public String userId;     // ID for the user of this file
  public String name;       // Name of this file
  public int total;        // Total number of expected chunks
  public List<FileChunk> chunk = new ArrayList<>();
  // ================ Class initialization ============
  public UserFile(String sUser, String sName, int iTotal, ErrHandle oErr) {
    this.userId = sUser;
    this.name = sName;
    this.errHandle = oErr;
    this.total = iTotal;
  }
  
  // ================ Public methods ==================
  /**
   * AddChunk -- Add one chunk to the list
   * 
   * @param sText
   * @param iChunk
   * @param iTotal
   * @return 
   */
  public boolean AddChunk(String sText, int iChunk, int iTotal) {
    try {
      // Create a new chunk
      FileChunk oChunk = new FileChunk();
      oChunk.number = iChunk;
      oChunk.total = iTotal;
      oChunk.fileName = this.name;
      oChunk.text = sText;
      synchronized(chunk) {
        // Add this chunk to the list of chunks
        this.chunk.add(oChunk);
      }
      // Return positively
      return true;
    } catch (Exception ex) {
      errHandle.DoError("UserFile/AddChunk: ", ex);
      return false;
    }    
  }
  
  /**
   * Write -- Write all the chunks buffered to a file
   * 
   * @param sFileName
   * @return 
   */
  public boolean Write(String sFileName) {
    try {
      File file = new File(sFileName);
      try (FileOutputStream foThis = new FileOutputStream(file); 
        OutputStreamWriter osThis = new OutputStreamWriter(foThis, "UTF-8"); 
        BufferedWriter writer = new BufferedWriter(osThis)) {
        // Loop through the chunks
        for (int i=0;i<this.total;i++) {
          // What is the chunk number?
          int iChunk = i+1;
          // Get the index of the element that has this chunk number
          int iHas = -1;
          for (int j=0;j<this.chunk.size();j++) {
            if (this.chunk.get(j).number == iChunk) {
              iHas = j;
              break;
            }
          }
          // Do we have it?
          if (iHas>=0) {
            // Access this chunk
            FileChunk oThis = this.chunk.get(iHas);
            // Write this chunk away
            writer.write(oThis.text);
            // =========== DEBUG ===================
            errHandle.debug("dbupload/Write chunk "+iChunk+" from location "+iHas);
            // =====================================
          } else {
            // =========== DEBUG ===================
            errHandle.debug("dbupload/Write cannot find chunk: "+iChunk);
            // =====================================
          }
          
        }
        // Flush the remainder
        writer.flush();
        // And then close it myself
        writer.close();
      }
      
      // Return positively
      return true;
    } catch (Exception ex) {
      errHandle.DoError("UserFile/Write: ", ex);
      return false;
    }    
  }
  
  /**
   * IsReady -- Check if all the chunks have been read
   * 
   * @return 
   */
  public boolean IsReady() {
    try {
      // Check if the size of the list equals the total expected number
      return (this.total == chunk.size());
    } catch (Exception ex) {
      errHandle.DoError("UserFile/IsReady: ", ex);
      return false;
    }   
  }
  
  /**
   * Clear -- Clear the current list of file chunks
   * 
   * @return 
   */
  public synchronized boolean Clear() {
    try {
      chunk.clear();
      // Check if the size of the list equals the total expected number
      return (this.total == chunk.size());
    } catch (Exception ex) {
      errHandle.DoError("UserFile/Clear: ", ex);
      return false;
    }   
  }
  
  /**
   * getChunk -- get the chunk with the indicated chunk number (starting at 1)
   * 
   * @param i
   * @return 
   */
  public String getChunk(int i) {
    try {
      // Validate
      if (i > this.total) return "";
      // Get the name of the chunk file
      String sText = this.chunk.get(i-1).text;
      // Return it
      return sText;
    } catch (Exception ex) {
      errHandle.DoError("UserFile/getChunk: ", ex);
      return "";
    }
  }
  
  // ================ Private methods =================
  
}
/**
 * FileChunk -- one chunk in a file
 * 
 * @author Erwin
 */
class FileChunk {
  public int number;        // Number of this chunk
  public int total;         // Total number of chunks
  public String fileName;   // Name of the file this belongs to
  public String text;       // Text of this chunk 
}
