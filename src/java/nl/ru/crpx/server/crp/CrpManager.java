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
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import nl.ru.crpx.dataobject.DataObject;
import nl.ru.crpx.dataobject.DataObjectList;
import nl.ru.crpx.dataobject.DataObjectMapElement;
import nl.ru.crpx.project.CorpusResearchProject;
import nl.ru.crpx.server.CrpPserver;
import static nl.ru.crpx.server.crp.CrpUser.sProjectBase;
import nl.ru.crpx.tools.ErrHandle;
import nl.ru.util.ByRef;
import nl.ru.util.DateUtil;
import nl.ru.util.FileUtil;
import nl.ru.util.json.JSONArray;
import nl.ru.util.json.JSONObject;
import nl.ru.xmltools.XmlDocument;
import nl.ru.xmltools.XmlIndexRaReader;
import nl.ru.xmltools.XmlNode;

/**
 * CrpManager
 *    Manage the CRP projects that are worked on by the different users
 * 
 * @author Erwin R. Komen
 */
public class CrpManager {
  // ================ Local variables ==========================================
  ErrHandle errHandle;                  // The error handler we are using
  // ========================================== Constants ======================
  protected static final QName loc_xq_ResId = new QName("", "", "ResId");
  protected static final String loc_path_Result = "./descendant-or-self::Result[1]";
  protected static final String loc_path_General = "./descendant-or-self::General[1]";
  // ================ Static variables =========================================
  static List<CrpUser> loc_crpUserList; // List of CrpUser elements
  static int loc_id;                    // the Id of each CrpUser element
  static CrpPserver servlet;            // My own link to the search manager
  static Processor objSaxon;
  static DocumentBuilder objSaxDoc;
  // ================ Class initialisation =====================================
  public CrpManager(CrpPserver servlet, ErrHandle errHandle) {
    // Initialize the id
    this.loc_id = -1;
    // Set the error handler
    this.errHandle = errHandle;
    // Set my own link to the search manager
    this.servlet = servlet;
    // Get a new saxon processor
    this.objSaxon = new Processor(false);
    // Create a document builder
    this.objSaxDoc = this.objSaxon.newDocumentBuilder();
    // Initialise the list
    loc_crpUserList = new ArrayList<>();
  }
  
  /**
   * existsCrp
   *    True if the CRP with the indicated project name exists for that user
   * 
   * @param sProjectName
   * @param sUserId
   * @return 
   */
  public boolean existsCrp(String sProjectName, String sUserId) {
    try {
      String sProjectPath = sProjectName;
      
      // Set the project path straight
      if (!sProjectPath.contains("/")) {
        String sProjStart = (sProjectBase.endsWith("/")) ? sProjectBase :
                sProjectBase + "/";
        sProjectPath = FileUtil.nameNormalize(sProjStart + sUserId + "/" + sProjectPath);
        if (!sProjectPath.contains(".")) {
          sProjectPath += ".crpx";
        }
      }
      // Create a file handle to it
      File fPrj = new File(sProjectPath);
      // Return the existence of this file
      return fPrj.exists();
    } catch (Exception ex) {
      errHandle.DoError("Problem in existsCrp", ex, CrpManager.class);
      return false;
    }
  }
  
  /**
   * getCrpUser
   * Either create a new CrpUser object, or get an already existing one
   * 
   * @param sProjectName
   * @param sUserId
   * @param sAction       - Either 'load' or 'create' 
   * @return 
   */
  public CrpUser getCrpUser(String sProjectName, String sUserId, String sAction) {
    try {
      // Make sure anything is set without the .crpx extension
      if (sProjectName.endsWith(".crpx")) sProjectName = sProjectName.replace(".crpx", "");
      // Check if this combination already exists in the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Check if this has the correct project name, language index and user id
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)) {
            errHandle.debug("getCrpUser - reusing: [" + sProjectName + 
                    ", " + sUserId + "]", CrpManager.class);
            // Return this object
            return oCrpUser;
          }
        } 
      // Getting here means that we need to create a new entry
      CrpUser oNewCrpUser = new CrpUser(servlet, sProjectName, sAction, sUserId, errHandle);
      // Have we succeeded?
      if (errHandle.bInterrupt || errHandle.hasErr()) {
        // There have been errors
        errHandle.bInterrupt = true;
        return null;
      }
      // Add this to the list
      loc_crpUserList.add(oNewCrpUser);
      String sChanged = DateUtil.dateToString(oNewCrpUser.prjThis.getDateChanged());
      errHandle.debug("getCrpUser - adding: [" + sProjectName + 
              ", " + sUserId + ", " + sChanged + "]", CrpManager.class);
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
      // Validate existence
      if (!this.existsCrp(sProjectName, sUserId)) return null;
      // File must exist, so continue
      CrpUser oCrpUser= getCrpUser(sProjectName, sUserId, "load");
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
   * createCrp
   *    Create a CRP with the indicated name for the indicated user
   * 
   * @param sProjectName
   * @param sUserId
   * @return 
   */
  public CorpusResearchProject createCrp(String sProjectName, String sUserId) {
    try {
      CrpUser oCrpUser= getCrpUser(sProjectName, sUserId, "create");
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
        oSettings.put("dbases", new JSONArray());
        // Write the default settings
        setUserSettings(sUserId, oSettings);
      }
      // Double check existence
      if (fSettings.exists()) {
        // Read the contents
        oSettings = new JSONObject((new FileUtil()).readFile(fSettings));
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
      DataObjectList arDbaseList = new DataObjectList("dbases");
      // Try get settings file
      String sFile = FileUtil.nameNormalize(sProjectBase+sUserId+"/settings.json");
      File fSettings = new File(sFile);
      // Check existence
      if (fSettings.exists()) {
        // Read the settings as JSON object
        JSONObject oJsonSet = new JSONObject((new FileUtil()).readFile(fSettings));
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
              break;
            case "dbases":
              // This is an array of objects
              JSONArray arDbases = oJsonSet.getJSONArray("dbases");
              for (int i=0;i<arDbases.length(); i++) {
                // Get this object
                JSONObject oThis = arDbases.getJSONObject(i);
                // Create a new object
                DataObjectMapElement oLink = new DataObjectMapElement();
                oLink.put("dbase", oThis.getString("dbase"));
                oLink.put("lng", oThis.getString("lng"));
                oLink.put("dir", oThis.getString("dir"));
                // Add the object to the list
                arDbaseList.add(oLink);
              }
              // Add this element to the settings object
              oSettings.put("links", arDbaseList);
              break;
          }
        }
      } else {
        // Create a default one
        oSettings.put("userid", sUserId);
        oSettings.put("links", arLinksList);
        oSettings.put("dbases", arDbaseList);
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
   * addUserSettingsDbLng
   *    Add a link between a database and Lng/Dir for a user
   * 
   * @param sUserId
   * @param sDbName
   * @param sLng
   * @param sDir 
   */
  public void addUserSettingsDbLng(String sUserId, String sDbName,
          String sLng, String sDir) {
    JSONArray arDbases; // Array of dbase objects
    
    try {
      // Read the current settings
      JSONObject oSettings = getUserSettings(sUserId);
      // Validate
      if (oSettings==null) {
        errHandle.DoError("Could not add to user settings");
        return;
      }
      // Get the JSON databases array
      if (oSettings.has("dbases")) 
        arDbases = oSettings.getJSONArray("dbases");
      else
        arDbases = new JSONArray();
      
      // Does the link exist already?
      boolean bExists = false;
      JSONObject oDbase = null;
      for (int i=0;i<arDbases.length();i++) {
        oDbase = arDbases.getJSONObject(i);
        if (oDbase.getString("dbase").equals(sDbName)) {
          bExists = true;
          // Adapt the information
          oDbase.put("lng", sLng);
          oDbase.put("dir", sDir);
          // Replace it
          arDbases.put(i, oDbase);
          break;
        }
      }
      // Do we have it?
      if (!bExists) {
        // Add the link
        oDbase = new JSONObject();
        oDbase.put("dbase", sDbName);
        oDbase.put("lng", sLng);
        oDbase.put("dir", sDir);
        arDbases.put(oDbase);
      }
      // Add or replace the information
      oSettings.put("dbases", arDbases);
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
   * @param sCrpName
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
   * getUserLinkDb
   *    Check for user sUserId if he has a default LNG/DIR for database sDbName
   * 
   * @param sUserId   - Identifier of the user
   * @param sDbName   - Name of the database
   * @return 
   */
  public JSONObject getUserLinkDb(String sUserId, String sDbName) {
    JSONArray arDbases;
    try {
      // Read the current settings
      JSONObject oSettings = getUserSettings(sUserId);
      // Validate
      if (oSettings==null) {
        errHandle.DoError("Could not get user settings");
        return null;
      }
      // Get the JSON linking array for databases
      if (oSettings.has("dbases"))
        arDbases = oSettings.getJSONArray("dbases");
      else
        arDbases = new JSONArray();
      // Does the database link exist?
      JSONObject oDbase = null;
      for (int i=0;i<arDbases.length();i++) {
        oDbase = arDbases.getJSONObject(i);
        if (oDbase.getString("dbase").equals(sDbName)) {
          // Return the link object
          return oDbase;
        }
      }
      // Getting here means: no result
      return null;
    } catch (Exception ex) {
      errHandle.DoError("Could not get user link dbase info", ex, CrpManager.class);
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
    int iCrpId = 0;       // Identifier of CRP on the list
    
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
                iCrpId++;
                oData.put("CrpId", iCrpId);
                oData.put("userid", sUser);
                oData.put("crp", sCrp);
                oData.put("loaded", bLoaded);
                oData.put("size", pathCrp.toFile().length() );
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
   * getDbList -- get a list of the corpus research databases (.xml) for the
   *              indicated user
   * 
   * @param sUserId
   * @return 
   */
  public DataObject getDbList(String sUserId) {
    return getDbList(sUserId, "*.xml");
  }
  public DataObject getDbList(String sUserId, String sFileName) {
    String sUserPath;     // Where the users are stored
    List<String> lUsers;  // List of crpx
    
    try {
      // Adapt the filter [sFileName] if required
      if (! sFileName.endsWith(".xml")) {
        // Check if it is empty
        if (sFileName.isEmpty()) 
          sFileName = "*.xml";
        else {
          // Add the ".xml" extension
          sFileName += ".xml";
        }
      }
      // Create a list to reply
      DataObjectList arList = new DataObjectList("dblist");
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
            // Get to the "dbase" directory of this user
            String sUserDb = pathUser.toString() + "/dbase";
            File fUserDb = new File(sUserDb);
            // Does this file exist?
            if (fUserDb.exists()) {
              Path pathUserDb = fUserDb.toPath();
              // Get all the Database .xml files in the user's directory
              DirectoryStream<Path> streamDb = Files.newDirectoryStream(pathUserDb, sFileName);
              for (Path pathDb : streamDb) {
                // Get the name of this database
                String sDbase = pathDb.getFileName().toString();
                // Okay, create a reply object
                DataObjectMapElement oData = new DataObjectMapElement();
                oData.put("userid", sUser);
                oData.put("dbase", sDbase);
                String sDbPath = pathDb.toString();
                oData.put("file", sDbPath);
                // Get any lng/dir info
                JSONObject oDbase = getUserLinkDb(sUserId, sDbase);
                if (oDbase == null) {
                  String sLng = "";
                  String sDir = "";
                  // Make sure the User/dbase combination is stored
                  XmlNode ndxHeader = getDbaseHeader(pathDb.toString());
                  if (ndxHeader != null) {
                    XmlNode ndxLang = ndxHeader.SelectSingleNode("./descendant::Language");
                    if (ndxLang != null) {
                      sLng = ndxLang.getNodeValue();
                    }
                    XmlNode ndxDir = ndxHeader.SelectSingleNode("./descendant::Part");
                    if (ndxDir != null) {
                      sDir = ndxDir.getNodeValue();
                    }
                  }
                  addUserSettingsDbLng(sUserId, sDbase, sLng, sDir);
                  // Try get the link once more
                  oDbase = getUserLinkDb(sUserId, sDbase);
                } 
                // Do we have some kind of linking information?
                if (oDbase != null) {
                  // Add the lng and dir info
                  oData.put("lng", oDbase.getString("lng"));
                  oData.put("dir", oDbase.getString("dir"));
                }
                // Include the object here
                arList.add(oData);
              }
            }
          }
        }        
      }
      
      // Sort the result
      arList.sort("dbase");
      // Return the array
      return arList;      
    } catch (Exception ex) {
      errHandle.DoError("Could not get a list of Database-User objects", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getDbaseHeader - get the header of the database as XML node
   * 
   * @param sDbPath
   * @return 
   */
  private XmlNode getDbaseHeader(String sDbPath) {
    XmlNode ndxBack = null; // What we will return
    
    try {
      // Set a new XML document
      XmlDocument pdxThis = new XmlDocument(this.objSaxDoc, this.objSaxon);
      // (1) get a handle to the database
      File fDbase = new File(sDbPath);
      XmlIndexRaReader fDbRa = new XmlIndexRaReader(fDbase, null, pdxThis, CorpusResearchProject.ProjType.Dbase);
      // Do we have a header?
      String sHeader = fDbRa.getHeader();
      if (!sHeader.isEmpty()) {
        // Load this obligatory <General> header 
        pdxThis.LoadXml(sHeader);
        ndxBack =pdxThis.SelectSingleNode(loc_path_General);
      }
      // Properly close the RaReader
      fDbRa.close();
      // Return the result
      return ndxBack;
    } catch (Exception ex) {
      errHandle.DoError("Could not get database header", ex, CrpManager.class);
      return null;
    }
  }
  
  /**
   * getDbPath -- get the absolute path to database [sDbName] for user [sUserId]
   * 
   * @param sDbName - name of the database with or without the extension .xml
   * @param sUserId - id of user
   * @return        - absolute path to database
   * @history
   *  28/sep/2015 ERK Created
   */
  public String getDbPath(String sDbName, String sUserId) {
    try {
      // Do we need to add the database extension?
      if (!sDbName.endsWith(".xml")) sDbName += ".xml";
      // Figure out the expected location
      String sDbLoc = FileUtil.nameNormalize(sProjectBase + "/dbase/" + sUserId + "/" + sDbName);
      // Return the expected location
      return sDbLoc;
    } catch (Exception ex) {
      errHandle.DoError("Could not get database path", ex, CrpManager.class);
      return "";      
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
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)) {
          // We found it: now remove it
          int iCount = loc_crpUserList.size();
          loc_crpUserList.remove(oCrpUser);
          int iAfter = loc_crpUserList.size();
          // Give a report to the user
          errHandle.debug("removing CrpUser[1]: [" + sProjectName + 
              ", " + sUserId + "] (before="+iCount+", after="+iAfter+")", CrpManager.class);
          /*
          // Show the contents
          for (int i=0;i<loc_crpUserList.size();i++) {
            CrpUser oThis = loc_crpUserList.get(i);
            errHandle.debug("  AFTER  "+(i+1)+"="+oThis.prjName+"/"+oThis.userId);
          }*/
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
/**
   * removeDbUser
   * Remove the DbUser object satisfying the indicated conditions
   * 
   * @param sProjectName
   * // @param sLngIndex
   * @param sUserId
   * @return 
   */
  public boolean removeDbUser(String sProjectName, String sUserId) {
    try {
      // Check if this combination already exists in the list
      for (CrpUser oCrpUser : loc_crpUserList) {
        // Check if this has the correct project name, language index and user id
        if (oCrpUser.prjName.equals(sProjectName) && oCrpUser.userId.equals(sUserId)) {
          // We found it: now remove it
          errHandle.debug("removeDbUser/removing CrpUser[db]: [" + sProjectName + 
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
