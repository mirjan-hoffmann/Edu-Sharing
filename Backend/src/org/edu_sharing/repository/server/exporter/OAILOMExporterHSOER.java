package org.edu_sharing.repository.server.exporter;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.apache.log4j.Logger;
import org.edu_sharing.metadataset.v2.MetadataKey;
import org.edu_sharing.metadataset.v2.MetadataReaderV2;
import org.edu_sharing.metadataset.v2.MetadataSetV2;
import org.edu_sharing.metadataset.v2.MetadataWidget;
import org.edu_sharing.metadataset.v2.tools.MetadataHelper;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.client.tools.forms.VCardTool;
import org.edu_sharing.repository.server.tools.ApplicationInfoList;
import org.edu_sharing.repository.server.tools.URLTool;
import org.edu_sharing.repository.server.tools.VCardConverter;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


// please refer to
// https://dini-ag-kim.github.io/hs-oer-lom-profil/latest/
public class OAILOMExporterHSOER extends OAILOMExporter {

    Logger logger = Logger.getLogger(OAILOMExporterHSOER.class);

    final String nsHSFaecher = "https://w3id.org/kim/hochschulfaechersystematik/scheme";
    final String nsLRT = "https://w3id.org/kim/hcrt/scheme";
    final String nsLOM = "LOMv1.0";

    public OAILOMExporterHSOER() throws ParserConfigurationException {
       super();
       this.xmlLanguageAttribute = "xml:lang";
    }


    @Override
    public void createNameSpace(Element lom) {
        lom.setAttribute("xmlns", "https://www.oerbw.de/hsoerlom");
        lom.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        lom.setAttribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema-instance");
        lom.setAttribute("xsi:schemaLocation", "https://www.oerbw.de/hsoerlom https://w3id.org/dini-ag-kim/hs-oer-lom-profil/latest/schemas/hs-oer-lom.xsd");
    }

    @Override
    public Element createIdentifier(Element general){
        Element identifier = createAndAppendElement("identifier", general);
        createAndAppendElement("catalog",identifier,lomIdentifierCatalog, false);
        Element identifierEntry = createAndAppendElement("entry",identifier);
        createAndAppendElement("langstring",identifierEntry,QName.createQName(CCConstants.SYS_PROP_NODE_UID));
        return identifier;
    }

    @Override
    public Element addContributer(Element eleParent, QName contributerProp, String role) {
        Element contributeEle = null;
        Serializable contributer = nodeService.getProperty(nodeRef, contributerProp);
        if (contributer != null || role.equals("Provider")) {
            boolean hasValidEls = false;
            List<String> contrib = null;
            //sometimes there are empty values in list
            if (contributer instanceof List) {
                contrib = (List) contributer;
                for (String cont : contrib) {
                    if (cont != null && !cont.trim().equals(""))
                        hasValidEls = true;
                }
            }

            // Hack for "Herkunft" : shall be metametadata - Provider
            if (!hasValidEls && role.equals("Provider")) {
                String university = (String) nodeService.getProperty(nodeRef, QName.createQName(CCConstants.CCM_PROP_IO_UNIVERSITY));
                if (university != null && !university.isEmpty()) {
                    if (contrib == null)
                        contrib = new ArrayList<String>();
                    try {
                        Map<String, MetadataKey> valuesAsMap = MetadataHelper.getLocalDefaultMetadataset().findWidget("ccm:university").getValuesAsMap();
                        for(MetadataKey metadataKey : valuesAsMap.values()){
                            if(metadataKey.getKey().equals(university)){

                                HashMap<String,String> map = new HashMap<>();
                                map.put(CCConstants.VCARD_ORG,metadataKey.getCaption());
                                map.put(CCConstants.VCARD_URL,"https://"+university);

                                contrib.add( VCardTool.hashMap2VCard(map));
                                hasValidEls = true;
                            }
                        }
                    } catch (Throwable e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }

            if (hasValidEls) {
                contributeEle = createAndAppendElement("contribute", eleParent);
                createAndAppendElementSrcVal("role", contributeEle, role, nsLOM);
                Element centityEle = null;
                for (Object lval : contrib) {
                    centityEle = createAndAppendElement("centity", contributeEle);
                    // ## Add URL fields for ORCID/GND-ID resp. ROR/Wikidata-URL

                    ArrayList<HashMap<String, Object>> hashMaps = VCardConverter.vcardToHashMap((String) lval);
                    if(hashMaps.size() > 0){
                        HashMap<String, Object> map = hashMaps.iterator().next();
                        Map<String,String> newMap = map.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> (String)e.getValue()));
                        newMap.remove(CCConstants.VCARD_EXT_LOM_CONTRIBUTE_DATE);
                        String val = VCardTool.hashMap2VCard(new HashMap(newMap));
                        this.createAndAppendElement("vcard", centityEle, val, true);
                    }
                }
            }
        }
        return contributeEle;
    }


    @Override
    public void appendMLText(MLText mlText, Element mlElement) {
        for (Map.Entry<Locale, String> entry : mlText.entrySet()) {
            Element langEle = this.createAndAppendElement("langstring", mlElement, entry.getValue());
            if(entry.getKey() != null) {
                String langId = entry.getKey().getLanguage();
                if (langId != null && !langId.isEmpty())
                    langEle.setAttribute(xmlLanguageAttribute, langId);
            }
        }
    }

    @Override
    public Element createAndAppendElement(String elementName, Element parent) {
        if (elementName != null && !elementName.isEmpty() && parent != null) {
            Element element = doc.createElement(elementName);
            parent.appendChild(element);
            return element;
        }
        return null;
    }

   /* @Override
    public Element createGeneral(Element lom) {
        Element general = createAndAppendElement("general", lom);
        createAndAppendElement("identifier", general, QName.createQName(CCConstants.SYS_PROP_NODE_UID));
        createHandle(general);
        return general;
    }

    @Override
    public Element createHandle(Element eleParent) {
        MLText desc = new MLText(Locale.ROOT, "");
        String handleId = (String)nodeService.getProperty(nodeRef,QName.createQName(CCConstants.CCM_PROP_PUBLISHED_HANDLE_ID));
        Element catalogentryEle = null;
        if(handleId != null && !handleId.trim().equals("")){
            catalogentryEle = createAndAppendElement("catalogentry", eleParent);
            // fixed values: 'HDL' - Handle, 'DOI' or 'URN'
            createAndAppendElement("catalog", catalogentryEle, "HDL", false);
            desc.addValue(Locale.ROOT, handleId);
            createAndAppendElement("entry", catalogentryEle, desc, false);
        }
        return null;
    }*/

    @Override
    public void createTitle(Element general) {
        QName prop = QName.createQName(CCConstants.LOM_PROP_GENERAL_TITLE);
        String str = (String) nodeService.getProperty(nodeRef, prop);
        if (str == null || str.isEmpty())
            prop = QName.createQName(CCConstants.CM_NAME);
        createAndAppendElementLangStr("title", general, prop, null);
    }

    @Override
    public void createLanguage(Element general) {
        QName prop = QName.createQName(CCConstants.LOM_PROP_GENERAL_LANGUAGE);
        ArrayList<String> lang = (ArrayList<String>) nodeService.getProperty(nodeRef, prop);
        if (lang != null && !lang.isEmpty() && !(lang.size() == 1 && lang.get(0).contentEquals("unknown")))
            createAndAppendElement("language", general, prop);
    }

    @Override
    public void createDescription(Element general) {
        createAndAppendElementLangStr("description", general,
                QName.createQName(CCConstants.LOM_PROP_GENERAL_DESCRIPTION),null);
    }

    /**
     * keywords are merged values of fixed and free keywords - langstring
     *
     * @param general
     */
    @Override
    public void createKeyword(Element general) {
        createAndAppendElementLangStr("keyword", general, QName.createQName(CCConstants.LOM_PROP_GENERAL_KEYWORD),null);
        createAndAppendElementLangStr("keyword", general, QName.createQName(CCConstants.CCM_PROP_IO_REPL_CLASSIFICATION_KEYWORD),Locale.GERMAN);
    }


    /**
     *  aggregationLevel (not implemented in ZOERR)
     *  createAndAppendElementSrcVal("aggregationLevel", general, QName.createQName(CCConstants.LOM_PROP_GENERAL_AGGREGATIONLEVEL), nsLOM);
     *  if obj == series (Serienobjekt) then value = 2 otherwise value = 1
     */
    @Override
    public void createAggregationLevel(Element general) {
        NodeRef parent = nodeService.getPrimaryParent(nodeRef).getParentRef();
        String val = "1";

        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(nodeRef, QName.createQName(CCConstants.CCM_ASSOC_CHILDIO), null);
        if (childAssocs != null && childAssocs.size() > 0){
            val="2";
        }

        createAndAppendElementSrcVal("aggregationLevel", general, val, nsLOM);
    }

    @Override
    public void createLifecycle(Element lom) {
        //lifecycle
        Element lifecycle = createAndAppendElement("lifecycle", lom);
        // version
        Element versionEle = createAndAppendElement("version", lifecycle);
        createAndAppendElement("langstring", versionEle, QName.createQName(CCConstants.LOM_PROP_LIFECYCLE_VERSION));
        // status - for now is always "Final" (allowed is "Draft", "Revised", Unavailable" too)
        String status = "Draft";

        String unGuest = ApplicationInfoList.getHomeRepository().getGuest_username();
        if(unGuest == null) unGuest = PermissionService.GUEST_AUTHORITY;
        AccessStatus sharedForEveryOne = AuthenticationUtil.runAs(()->{return serviceRegistry.getPermissionService().hasPermission(nodeRef, PermissionService.CONSUMER);}, unGuest);
        if(sharedForEveryOne.equals(AccessStatus.ALLOWED)){
            status = "Final";
        }
        createAndAppendElementSrcVal("status", lifecycle, status, nsLOM);
        // contribute
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_AUTHOR), "Author");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_CONTENT_PROVIDER), "Content Provider");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_EDITOR), "Editor");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_EDUCATIONAL_VALIDATOR), "Educational Validator");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_GRAPHICAL_DESIGNER), "Graphical Designer");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_INITIATOR), "Initiator");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_INSTRUCTIONAL_DESIGNER), "Instructional Designer");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_PUBLISHER), "Publisher");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_SCRIPT_WRITER), "Script Writer");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_VALIDATOR), "Validator");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_TECHNICAL_IMPLEMENTER), "Technical Implementer");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_TECHNICAL_VALIDATOR), "Technical Validator");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_TERMINATOR), "Terminator");
        addContributer(lifecycle, QName.createQName(CCConstants.CCM_PROP_IO_REPL_LIFECYCLECONTRIBUTER_UNKNOWN), "Unknown");
    }

    @Override
    public void createMetadata(Element lom) {
        MLText desc = new MLText(Locale.ROOT, "");
        //metametadata
        Element category = doc.createElement("metametadata");
        desc.clear();
        desc.addValue(Locale.ROOT, "Modified");
        // creator with date of last change (no matter if content or metadata)
        addContributer(category, QName.createQName(CCConstants.CCM_PROP_IO_REPL_METADATACONTRIBUTER_CREATOR), "Creator",
                CCConstants.CM_PROP_C_MODIFIED, desc);
        // includes extra prop "Herkunft" - noch umsetzen! ##
        addContributer(category, QName.createQName(CCConstants.CCM_PROP_IO_REPL_METADATACONTRIBUTER_PROVIDER), "Provider");
        // validator with publishing date - ## Please activate Published date !
        desc.clear();
        desc.addValue(Locale.ROOT, "Published");
        addContributer(category, QName.createQName(CCConstants.CCM_PROP_IO_REPL_METADATACONTRIBUTER_VALIDATOR), "Validator",
                CCConstants.CCM_PROP_IO_PUBLISHED_DATE, desc);
        if (category.hasChildNodes())
            lom.appendChild(category);
    }

    public Element addContributer(Element eleParent, QName contributerProp, String role, String dateCC, MLText desc) {
        Element contributeEle = addContributer(eleParent, contributerProp, role);
        addDateTime("date", contributeEle, dateCC, desc);
        return contributeEle;
    }

    @Override
    public void createTechnical(Element lom) {
        //technical
        Element category = doc.createElement("technical");
        // format - as MIME string
        createAndAppendElement("format", category, QName.createQName(CCConstants.LOM_PROP_TECHNICAL_FORMAT));
        // size
        createAndAppendElement("size", category, QName.createQName(CCConstants.LOM_PROP_TECHNICAL_SIZE));
        // location as URI (attribute "type" can be "URI" or "TEXT")
        Element locationEle = createAndAppendElement("location", category, URLTool.getNgRenderNodeUrl(nodeRef.getId(), null));
        locationEle.setAttribute("type", "URI");
        // otherplatformrequirements
        createAndAppendElement("otherplatformrequirements", category, QName.createQName(CCConstants.LOM_PROP_TECHNICAL_OTHERPLATFORMREQUIREMENTS));
        MLText desc = new MLText(Locale.ROOT, "");
        // duration - should be ISO8601 format
        desc.clear();
        desc.addValue(Locale.ROOT, "Playing time");
        addDateTime("duration", category, CCConstants.LOM_PROP_TECHNICAL_DURATION, desc);
        if (category.hasChildNodes())
            lom.appendChild(category);
    }

    @Override
    public void createStructure(Element general) {
        //do nothing here
    }

    @Override
    public void createEducational(Element lom) {
        //educational
        Element category = doc.createElement("educational");
        // learningResourceType - INCORRECT values so far
        createAndAppendElementSrcIdEnt("learningResourceType", category,
                QName.createQName(CCConstants.CCM_PROP_IO_REPL_EDUCATIONAL_LEARNINGRESSOURCETYPE), nsLRT);
        // description - langstring (not implemented in ZOERR)
        createAndAppendElement("description", category, QName.createQName(CCConstants.LOM_PROP_EDUCATIONAL_DESCRIPTION));
        if (category.hasChildNodes())
            lom.appendChild(category);
    }

    @Override
    public void createRights(Element lom) {
        MLText desc = new MLText(Locale.ROOT, "");
        //metametadata
        Element category = createAndAppendElement("rights", lom);
        boolean hasCopyright = false;
        List<String> commonLicenceKeyList = (List<String>) nodeService.getProperty(nodeRef, QName.createQName(CCConstants.CCM_PROP_IO_COMMONLICENSE_KEY));
        String commonLicenceKey = (commonLicenceKeyList != null && commonLicenceKeyList.size() > 0) ? commonLicenceKeyList.get(0) : null;
        String commonLicenseVersion = (String) nodeService.getProperty(nodeRef, QName.createQName(CCConstants.CCM_PROP_IO_COMMONLICENSE_CC_VERSION));
        if (commonLicenceKey != null && !commonLicenceKey.trim().equals("")) {
            hasCopyright = true;
            String urlKey = commonLicenceKey.toLowerCase().replaceAll("_", "-").replaceFirst("cc-", "");
            String url = "https://creativecommons.org/licenses/" + urlKey + "/" + commonLicenseVersion;

            if (commonLicenceKey.equals(CCConstants.COMMON_LICENSE_CC_ZERO)) {
                hasCopyright = false;
                url = CCConstants.COMMON_LICENSE_CC_ZERO_LINK.replace("deed.${locale}", "legalcode");
            }

            createAndAppendElementSrcVal("copyrightandotherrestrictions", category, hasCopyright ? "yes" : "no", nsLOM);
            Locale urlLocale = new Locale("x-t-cc-url");
            desc.clear();
            desc.addValue(urlLocale, url);
            createAndAppendElement("description", category, desc, false);
        } else {
            createAndAppendElementSrcVal("copyrightandotherrestrictions", category, QName.createQName(CCConstants.LOM_PROP_RIGHTS_COPY_RIGHT), nsLOM);
            // description - langstring
            Serializable repoValue = nodeService.getProperty(nodeRef, QName.createQName(CCConstants.LOM_PROP_RIGHTS_RIGHTS_DESCRIPTION));
            if (repoValue != null)
                createAndAppendElement("description", category, repoValue, false);
            else {
                desc.clear();
                desc.addValue(Locale.GERMAN, "Keine Lizenz festgelegt");
                createAndAppendElement("description", category, desc, false);
            }
        }
    }

    @Override
    public void createThumbnail(Element lom) {
        //do nothing
    }

    @Override
    public void createClassification(Element lom) {

        QName property = QName.createQName(CCConstants.CCM_PROP_IO_REPL_TAXON_ID);
        // classification
        List<String> taxonIdList = (List<String>) nodeService.getProperty(nodeRef, property);
        if (taxonIdList != null && !taxonIdList.isEmpty()) {
            try {
                //fachgebite.xml
                Map<String, MetadataKey> valuesAsMap = MetadataHelper.getLocalDefaultMetadataset().findWidget("ccm:taxonid").getValuesAsMap();
                Element category = createAndAppendElement("classification", lom);
                createAndAppendElementSrcVal("purpose", category, "Discipline", nsLOM);
                Element taxonPathEle = createAndAppendElement("taxonpath", category);
                Element srcEle = createAndAppendElement("source", taxonPathEle, nsHSFaecher, false);
                if (srcEle != null)
                    srcEle.setAttribute(xmlLanguageAttribute, "x-none");
                for (String taxonId : taxonIdList) {

                    Element taxon = createAndAppendElement("taxon", taxonPathEle);
                    createAndAppendIdEnt(taxon,property,taxonId);
/*                    for (MetadataKey cata : valuesAsMap.values())
                        if (cata.getKey().equals(taxonId))
                            createTaxon(taxonPathEle, cata,new ArrayList<>(valuesAsMap.values()));*/
                }
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    public Element createAndAppendElementSrcIdEnt(String elementName, Element parent, QName property, String src) {
        if (property != null) {
            Serializable repoValue = nodeService.getProperty(nodeRef, property);
            return this.createAndAppendElementSrcIdEntMultivalue(elementName, parent, property,repoValue, src);
        }
        return null;
    }

    // ## The IDs have to be extracted here! Maybe the implementation like in createTaxon() is more helpful!
    //    In that case set lang attribute to "de" or "en" in entry!
    public Element createAndAppendElementSrcIdEntMultivalue(String elementName, Element parent, QName property, Serializable repoValue, String src) {
        if (repoValue != null) {
            Element langEle = null;

            if (repoValue instanceof List) {
                Element ele = null;
                for (Object lval : (List) repoValue)
                    ele = this.createAndAppendElementSrcIdEntMultivalue(elementName, parent,property, (Serializable) lval, src);
                return ele;
            }

            Element element = createAndAppendElement(elementName, parent);
            if (src != null) {
                Element srcEle = createAndAppendElement("source", element);
                langEle = createAndAppendElement("langstring", srcEle, src, false);
                if (langEle != null)
                    langEle.setAttribute(xmlLanguageAttribute, "x-none");
            }

            createAndAppendIdEnt(element,property,(String)repoValue);

            return element;
        }
        return null;
    }

    public void createAndAppendIdEnt(Element parent, QName property, String repoValue){
        // ## set correct id !
        createAndAppendElement("id", parent, (String)repoValue);


        String metadataSet = (String)nodeService.getProperty(nodeRef,QName.createQName(CCConstants.CM_PROP_METADATASET_EDU_METADATASET));
        if(metadataSet == null) metadataSet = CCConstants.metadatasetdefault_id;


        try {

            Element entEle = createAndAppendElement("entry", parent);
            if (entEle != null) {
                String[] languages = new String[]{"de","en_US"};
                for(String language : languages) {
                    MetadataSetV2 mds = MetadataReaderV2.getMetadataset(ApplicationInfoList.getHomeRepository(), metadataSet, language);
                    MetadataWidget widget = mds.findWidget(CCConstants.getValidLocalName(property.toString()));
                    MetadataKey metadataKey = widget.getValuesAsMap().get(repoValue);

                    if (metadataKey != null) {
                        if(metadataKey.getLocale().equals(language.split("_")[0])) {
                            Element langEle = createAndAppendElement("langstring", entEle, metadataKey.getCaption(), false);
                            if (langEle != null)
                                langEle.setAttribute(xmlLanguageAttribute, language.split("_")[0]);
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error(e.getMessage(),e);
        }
    }
}