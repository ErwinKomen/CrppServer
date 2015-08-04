/*
 * This software has been developed at the "Meertens Instituut"
 *    for the CLARIN project "CorpusStudio-WebApplication".
 *  The application is based on the "CorpusStudio" program written by Erwin R. Komen
 *    while working for the Radboud University Nijmegen.
 *  The program and the source can be freely used and re-distributed.
 */
package nl.ru.crpx.server.crp;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import static nl.ru.crpx.server.crp.CrpUser.sProjectBase;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.ByRef;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;

/**
 * CrpManager
 *    Manage the CRP projects that are worked on by the different users
 * 
 * @author Erwin R. Komen
 */
public class CrpManager {
  // ================ Local variables ==========================================
  ErrHandle errHandle;                  // The error handler we are using
  // ================ Static variables =========================================
  static List<CrpUser> loc_crpUserList; // List of CrpUser elements
  static int loc_id;                    // the Id of each CrpUser element
  static CrpPserver servlet;            // My own link to the search manager
  // ================ Class initialisation =====================================
  public CrpManager(CrpPserver servlet, ErrHandle errHandle) {
    // Initialize the id
    this.loc_id = -1;
    // Set the error handler
    this.errHandle = errHandle;
    // Set my own link to the search manager
    this.servlet = servlet;
    // Initialise the list
    loc_crpUserList = new ArrayList<>();
  }
  
  /**
   * getCrpUser
   * Either create a new CrpUser object, or get an already existing one
   * 
   * @param sProjectName
   * @param sLngIndex
   * @param sUserId
   * @return 
   */
  public CrpUser getCrpUser(String sProjectName, String sUserId) {
    try {
      // Check if this combination already exists in the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Check if this has the correct project name, language index and user id
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)) {
          // Return this object
          return oCrpUser;
        } 
      } 
      // Getting here means that we need to create a new entry
      CrpUser oNewCrpUser = new CrpUser(servlet, sProjectName, sUserId, errHandle);
      // Have we succeeded?
      if (errHandle.bInterrupt || errHandle.hasErr()) {
        // There have been errors
        errHandle.bInterrupt = true;
        return null;
      }
      // Add this to the list
      loc_crpUserList.add(oNewCrpUser);
      errHandle.debug("adding CrpUser in getCrpUser: [" + sProjectName + 
              ", " + /* sLngIndex + ", " + */ sUserId + "]", CrpManager.class);
      // Return this newly created one
      return oNewCrpUser;
    } catch (Exception ex) {
      errHandle.DoError("Could not load or retrieve CRP", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getCrp
   * Retrieve (or load) the CRP belonging to the indicated project name, language and user
   * 
   * @param sProjectName
   * @param sUserId
   * @param oErr
   * @return 
   */
  public CorpusResearchProject getCrp(String sProjectName, String sUserId,
          ByRef<ErrHandle> oErr) {
    // Reset the errore handling
    errHandle.clearErr();
    // Link the error handle
    oErr.argValue = errHandle;
    return getCrp(sProjectName, sUserId);
  }
  public CorpusResearchProject getCrp(String sProjectName, String sUserId) {
    try {
      CrpUser oCrpUser= getCrpUser(sProjectName, /* sLngIndex, */ sUserId);
      // Check what we get back
      if (oCrpUser == null)
        return null;
      else
        return oCrpUser.prjThis;
    } catch (Exception ex) {
      errHandle.DoError("Could not load or retrieve CRP", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getUserSettings
   *    Read the user's "settings.json", if it is available
   * 
   * @param sUserId
   * @return 
   */
  public JSONObject getUserSettings(String sUserId) {
    JSONObject oSettings ;
    try {
      String sFile = FileUtil.nameNormalize(sProjectBase+sUserId+"/settings.json");
      File fSettings = new File(sFile);
      // Check existence
      if (!fSettings.exists()) {
        // Create a default one
        oSettings = new JSONObject();
        oSettings.put("userid", sUserId);
        oSettings.put("links", new JSONArray());
        // Wrtie the default settings
        setUserSettings(sUserId, oSettings);
      }
      // Double check existence
      if (fSettings.exists()) {
        // Read the contents
        oSettings = new JSONObject(FileUtil.readFile(fSettings));
        // Return what we found
        return oSettings;
      } else {
        return null;
      }
    } catch (Exception ex) {
      errHandle.DoError("Could not load user settings", ex, CrpManager.class);
      return null;
    }
  }
  /**
   * getUserSettingsObject
   *    Read the user's "settings.json", if it is available
   * 
   * @param sUserId
   * @return 
   */
  public DataObject getUserSettingsObject(String sUserId) {
    DataObjectMapElement oSettings = new DataObjectMapElement();
    try {
      // Default initialisations
      DataObjectList arLinksList = new DataObjectList("links");
      // Try get settings file
      String sFile = FileUtil.nameNormalize(sProjectBase+sUserId+"/settings.json");
      File fSettings = new File(sFile);
      // Check existence
      if (fSettings.exists()) {
        // Read the settings as JSON object
        JSONObject oJsonSet = new JSONObject(FileUtil.readFile(fSettings));
        // Copy known key/value pairs
        Iterator keys = oJsonSet.keys();
        while (keys.hasNext()) {
          String sKeyName = keys.next().toString();
          switch (sKeyName) {
            case "recent":
            case "userid":
              // Just copy the key/value
              oSettings.put(sKeyName, oJsonSet.getString(sKeyName));
              break;
            case "links":
              // This is an array of objects
              JSONArray arLinks = oJsonSet.getJSONArray("links");
              for (int i=0;i<arLinks.length(); i++) {
                // Get this object
                JSONObject oThis = arLinks.getJSONObject(i);
                // Create a new object
                DataObjectMapElement oLink = new DataObjectMapElement();
                oLink.put("crp", oThis.getString("crp"));
                oLink.put("lng", oThis.getString("lng"));
                oLink.put("dir", oThis.getString("dir"));
                // Add the object to the list
                arLinksList.add(oLink);
              }
              // Add this element to the settings object
              oSettings.put("links", arLinksList);
          }
        }
      } else {
        // Create a default one
        oSettings.put("userid", sUserId);
        oSettings.put("links", arLinksList);
      }
      // Return what we found
      return oSettings;
    } catch (Exception ex) {
      errHandle.DoError("Could not load user settings", ex, CrpManager.class);
      return null;
    }
  }
  /**
   * setUserSettings
   *    Set the "settings.json" file for user sUserId
   * 
   * @param sUserId
   * @param oSettings 
   */
  public void setUserSettings(String sUserId, JSONObject oSettings) {
    try {
      // Validate
      if (sUserId.isEmpty() || oSettings == null) return;
      String sFile = FileUtil.nameNormalize(sProjectBase+sUserId+"/settings.json");
      File fSettings = new File(sFile);
      // Save the settings
      FileUtil.writeFile(fSettings, oSettings.toString(1));
    } catch (Exception ex) {
      errHandle.DoError("Could not set user settings", ex, CrpManager.class);
    }
  }
  /**
   * addUserSettings
   *    Add a key/value pair to the user settings
   * 
   * @param sUserId
   * @param sKey
   * @param sValue 
   */
  public void addUserSettings(String sUserId, String sKey, String sValue) {
    try {
      // Read the current settings
      JSONObject oSettings = getUserSettings(sUserId);
      // Validate
      if (oSettings==null) {
        errHandle.DoError("Could not add to user settings");
        return;
      }
      // Add the key/value pair
      oSettings.put(sKey, sValue);
      // Save the adapted settings
      setUserSettings(sUserId, oSettings);
    } catch (Exception ex) {
      errHandle.DoError("Could not add to user settings", ex, CrpManager.class);
    }
  }
  /**
   * addUserSettingsCrpLng
   *    Add a link between CRP and Lng/Dir for a user
   * 
   * @param sUserId
   * @param sCrpName
   * @param sLng
   * @param sDir 
   */
  public void addUserSettingsCrpLng(String sUserId, String sCrpName, 
          String sLng, String sDir) {
    try {
      // Read the current settings
      JSONObject oSettings = getUserSettings(sUserId);
      // Validate
      if (oSettings==null) {
        errHandle.DoError("Could not add to user settings");
        return;
      }
      // Get the JSON linking array
      JSONArray arLinks = oSettings.getJSONArray("links");
      // Does the link exist already?
      boolean bExists = false;
      JSONObject oLink = null;
      for (int i=0;i<arLinks.length();i++) {
        oLink = arLinks.getJSONObject(i);
        if (oLink.getString("crp").equals(sCrpName)) {
          bExists = true;
          // Adapt the information
          oLink.put("lng", sLng);
          oLink.put("dir", sDir);
          // Replace it
          arLinks.put(i, oLink);
          break;
        }
      }
      // Do we have it?
      if (!bExists) {
        // Add the link
        oLink = new JSONObject();
        oLink.put("crp", sCrpName);
        oLink.put("lng", sLng);
        oLink.put("dir", sDir);
        arLinks.put(oLink);
      }
      // Add or replace the information
      oSettings.put("links", arLinks);
      // Save the adapted settings
      setUserSettings(sUserId, oSettings);
    } catch (Exception ex) {
      errHandle.DoError("Could not add to user settings", ex, CrpManager.class);
    }
  }
  
  /**
   * getUserLinkCrp
   *    Check for user sUserId if he has a default LNG/DIR for project sCrpName
   * 
   * @param sUserId
   * @param sCrpname
   * @return 
   */
  public JSONObject getUserLinkCrp(String sUserId, String sCrpName) {
    try {
            // Read the current settings
      JSONObject oSettings = getUserSettings(sUserId);
      // Validate
      if (oSettings==null) {
        errHandle.DoError("Could not add to user settings");
        return null;
      }
      // Get the JSON linking array
      JSONArray arLinks = oSettings.getJSONArray("links");
      // Does the link exist?
      JSONObject oLink = null;
      for (int i=0;i<arLinks.length();i++) {
        oLink = arLinks.getJSONObject(i);
        if (oLink.getString("crp").equals(sCrpName)) {
          // Return the link object
          return oLink;
        }
      }
      // Getting here means: no result
      return null;
    } catch (Exception ex) {
      errHandle.DoError("Could not get user link crp info", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getCrpList - get a list of the CRPs for the indicated user
   *              If no user is given: provide all users and all CRPs
   * 
   * @param sUserId
   * @param sFilter
   * @return 
   */
  public DataObject getCrpList(String sUserId, String sFilter) {
    return getCrpList(sUserId, sFilter, "*.crpx");
  }
  public DataObject getCrpList(String sUserId, String sFilter, String sFileName) {
    String sUserPath;     // Where the users are stored
    List<String> lUsers;  // List of crpx
    
    try {
      // Create a list to reply
      DataObjectList arList = new DataObjectList("crplist");
      
      // Get a path to the users
      sUserPath = FileUtil.nameNormalize(sProjectBase);
      if (sUserPath.isEmpty()) return arList;
      // Initialise
      lUsers = new ArrayList<>();
      Path dir = Paths.get(sUserPath);
      // Get all the items inside "dir"
      try(DirectoryStream<Path> streamUser = Files.newDirectoryStream(dir)) {
        // Walk all these items
        for (Path pathUser : streamUser) {
          // Add the directory to the list of users
          lUsers.add(pathUser.toAbsolutePath().toString());
          // Get the user
          String sUser = pathUser.getFileName().toString();
          // Is this the user we are looking for?
          if (sUserId.isEmpty() || sUser.equals(sUserId)) {
            // Get all the CRP files in the user's directory
            DirectoryStream<Path> streamCrp = Files.newDirectoryStream(pathUser, sFileName);
            for (Path pathCrp : streamCrp) {
              // Get the name of this crp
              String sCrp = pathCrp.getFileName().toString();
              // Check its status
              boolean bLoaded = hasCrpUser(sCrp, sUser);
              boolean bInclude;
              switch (sFilter) {
                case "loaded":
                  bInclude = bLoaded; break;
                case "not loaded": case "notloaded":
                  bInclude = !bLoaded; break;
                default:
                  bInclude = true; break;
              }
              if (bInclude) {
                // Okay, create a reply object
                DataObjectMapElement oData = new DataObjectMapElement();
                oData.put("userid", sUser);
                oData.put("crp", sCrp);
                oData.put("loaded", bLoaded);
                String sCrpPath = pathCrp.toString();
                oData.put("file", sCrpPath);
                // Get any lng/dir info
                JSONObject oLink = getUserLinkCrp(sUserId, sCrp);
                if (oLink != null) {
                  // Add the lng and dir info
                  oData.put("lng", oLink.getString("lng"));
                  oData.put("dir", oLink.getString("dir"));
                }
                // Include the object here
                arList.add(oData);
                // lSorted.add(oData);
              }
            }
          }
        }
      } catch(IOException ex) {
        errHandle.DoError("Could not get a list of CRP-User objects", ex, CrpManager.class);
      }
      /*
      // Sor the arraylist
      Comparator c = new Comparator() {

        @Override
        public int compare(Object t, Object t1) {
          DataObjectMapElement o1 = (DataObjectMapElement) t;
          DataObjectMapElement o2 = (DataObjectMapElement) t1;
          return o1.get("crp").toString().compareTo(o2.get("crp").toString());
        }
      };
      // Sort it
      Collections.sort(lSorted, c);
      // Convert ArrayList to DataObjectList
      arList.addAll(lSorted);
      */
      // Sort the result
      arList.sort("crp");
      // Return the array
      return arList;
    } catch (Exception ex) {
      errHandle.DoError("Could not get a list of CRP-User objects", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * hasCrpUser - does user @sUserId have the project named @sProjectName
   * 
   * @param sProjectFile
   * @param sUserId
   * @return 
   */
  public boolean hasCrpUser(String sProjectFile, String sUserId) {
    try {
      // Walk the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Does this belong to the indecated user?
        if (sUserId.isEmpty() || sUserId.equals(oCrpUser.userId)) {
          // Is this the correct project?
          if (oCrpUser.prjName.equals(sProjectFile)) {
            // Yes, return positively
            return true;
          }
        }
      }
      // Failure
      return false;
    } catch (Exception ex) {
      errHandle.DoError("Could not check existence of Crp/User combination", ex, CrpManager.class);
      return false;
    }
  }
  
  /**
   * removeCrpUser
   * Remove the CrpUser object satisfying the indecated conditions
   * 
   * @param sProjectName
   * // @param sLngIndex
   * @param sUserId
   * @return 
   */
  public boolean removeCrpUser(String sProjectName, /* String sLngIndex, */ 
          String sUserId) {
    try {
      // Check if this combination already exists in the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Check if this has the correct project name, language index and user id
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)
                /* && oCrpUser.lngIndex.equals(sLngIndex) */) {
          // We found it: now remove it
          errHandle.debug("removing CrpUser: [" + sProjectName + 
              ", " + /* sLngIndex + ", " + */  sUserId + "]", CrpManager.class);
          loc_crpUserList.remove(oCrpUser);
          // Return positively
          return true;
        } 
      } 
      // Return failure: we didn't find it
      return false;
    } catch (Exception ex) {
      errHandle.DoError("Could not remove CRP-User object", ex, CrpManager.class);
      return false;
    }
  }
}
