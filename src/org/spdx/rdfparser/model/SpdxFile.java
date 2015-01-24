/**
 * Copyright (c) 2015 Source Auditor Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
*/
package org.spdx.rdfparser.model;

import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.spdx.rdfparser.IModelContainer;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.RdfParserHelper;
import org.spdx.rdfparser.SpdxRdfConstants;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.model.Checksum.ChecksumAlgorithm;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

/**
 * A File represents a named sequence of information 
 * that is contained in a software package.
 * @author Gary O'Neall
 *
 */
public class SpdxFile extends SpdxItem implements Comparable<SpdxFile> {
	
	static final Logger logger = Logger.getLogger(SpdxFile.class.getName());
	
	enum FileType {fileType_application, fileType_archive,
		fileType_audio, fileType_binary, fileType_documentation,
		fileType_image, fileType_other, fileType_source, fileType_spdx,
		fileType_text, fileType_video};
	FileType[] fileTypes = new FileType[0];
	Checksum[] checksums;
	String[] fileContributors = new String[0];
	String noticeText;
	DoapProject[] artifactOf = new DoapProject[0];
	SpdxFile[] fileDependencies = new SpdxFile[0];

	/**
	 * @param id SPDX Identifier for the file.  Must be unique within the modelContainer.
	 * @param name fileName
	 * @param comment Comment on the file
	 * @param annotations annotations for the file
	 * @param relationships Relationships to this file
	 * @param licenseConcluded
	 * @param licenseInfoInFile
	 * @param copyrightText
	 * @param licenseComment
	 * @throws InvalidSPDXAnalysisException 
	 */
	public SpdxFile(String id, String name, String comment, Annotation[] annotations,
			Relationship[] relationships, AnyLicenseInfo licenseConcluded,
			AnyLicenseInfo[] licenseInfoInFile, String copyrightText,
			String licenseComment, FileType[] fileTypes, Checksum[] checksums,
			String[] fileContributors, String noticeText, DoapProject[] artifactOf) throws InvalidSPDXAnalysisException {
		super(name, comment, annotations, relationships, 
				licenseConcluded, licenseInfoInFile,
				copyrightText, licenseComment);
		setId(id);	//TODO: Consider moving this up to SpdxElement
		this.fileTypes = fileTypes;
		if (this.fileTypes == null) {
			this.fileTypes = new FileType[0];
		}
		this.checksums = checksums;
		this.fileContributors = fileContributors;
		if (this.fileContributors == null) {
			this.fileContributors = new String[0];
		}
		this.noticeText = noticeText;
		this.fileDependencies = new SpdxFile[0];
		this.artifactOf = artifactOf;
		if (this.artifactOf == null) {
			this.artifactOf = new DoapProject[0];
		}
	}

	/**
	 * @param modelContainer
	 * @param node
	 * @throws InvalidSPDXAnalysisException
	 */
	public SpdxFile(IModelContainer modelContainer, Node node)
			throws InvalidSPDXAnalysisException {
		super(modelContainer, node);
		String[] fileTypeUris = findUriPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_TYPE);
		this.fileTypes = urisToFileType(fileTypeUris, false);
		this.checksums = findMultipleChecksumPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_CHECKSUM);
		this.fileContributors = findMultiplePropertyValues(SpdxRdfConstants.SPDX_NAMESPACE,
				SpdxRdfConstants.PROP_FILE_CONTRIBUTOR);
		this.noticeText = findSinglePropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_NOTICE);
		// File dependencies
		SpdxElement[] fileDependencyElements = findMultipleElementPropertyValues(
				SpdxRdfConstants.SPDX_NAMESPACE, SpdxRdfConstants.PROP_FILE_FILE_DEPENDENCY);
		int count = 0;
		if (fileDependencyElements != null) {
			for (int i = 0; i < fileDependencyElements.length; i++) {
				if (fileDependencyElements[i] instanceof SpdxFile) {
					count++;
				}
			}
		}
		if (count > 0) {
			this.fileDependencies = new SpdxFile[count];
			int j = 0;
			for (int i = 0; i < fileDependencyElements.length; i++) {
				if (fileDependencyElements[i] instanceof SpdxFile) {
					this.fileDependencies[j++] = (SpdxFile)fileDependencyElements[i];
				}
			}
		} else {
			this.fileDependencies = new SpdxFile[0];
		}
		// ArtifactOfs
		this.artifactOf = findMultipleDoapPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE,
				SpdxRdfConstants.PROP_FILE_ARTIFACTOF);
	}

	/**
	 * Finds the resource for an existing file in the model
	 * @param spdxFile
	 * @return resource of an SPDX file with the same name and checksum.  Null if none found
	 * @throws InvalidSPDXAnalysisException 
	 */
	static protected Resource findFileResource(IModelContainer modelContainer, SpdxFile spdxFile) throws InvalidSPDXAnalysisException {
		// find any matching file names
		Model model = modelContainer.getModel();
		Node fileNameProperty = model.getProperty(SpdxRdfConstants.SPDX_NAMESPACE, SpdxRdfConstants.PROP_FILE_NAME).asNode();
		Triple fileNameMatch = Triple.createMatch(null, fileNameProperty, Node.createLiteral(spdxFile.getName()));
		
		ExtendedIterator<Triple> filenameMatchIter = model.getGraph().find(fileNameMatch);	
		if (filenameMatchIter.hasNext()) {
			Triple fileMatchTriple = filenameMatchIter.next();
			Node fileNode = fileMatchTriple.getSubject();
			// check the checksum
			Node checksumProperty = model.getProperty(SpdxRdfConstants.SPDX_NAMESPACE, SpdxRdfConstants.PROP_FILE_CHECKSUM).asNode();
			Triple checksumMatch = Triple.createMatch(fileNode, checksumProperty, null);
			ExtendedIterator<Triple> checksumMatchIterator = model.getGraph().find(checksumMatch);
			if (checksumMatchIterator.hasNext()) {
				Triple checksumMatchTriple = checksumMatchIterator.next();
				Checksum cksum = new Checksum(modelContainer, checksumMatchTriple.getObject());
				if (cksum.getAlgorithm().equals(ChecksumAlgorithm.checksumAlgorithm_sha1) &&
						cksum.getValue().compareToIgnoreCase(spdxFile.getSha1()) == 0) {
					return RdfParserHelper.convertToResource(model, fileNode);
				}
			}
		}
		// if we get to here, we did not find a match
		return null;
	}
	
	@Override
	protected Resource findDuplicateResource(IModelContainer modelContainer, String uri) throws InvalidSPDXAnalysisException {
		// see if we want to change what is considered a duplicate
		// currently, a file is considered a duplicate if the checksum and filename
		// are the same.
		return findFileResource(modelContainer, this);
	}
	
	/**
	 * @return the Sha1 checksum value for this file, or a blank string if no sha1 checksum has been set
	 */
	public String getSha1() {
		if (this.checksums != null) {
			for (int i = 0;i < this.checksums.length; i++) {
				if (this.checksums[i].getAlgorithm().equals(ChecksumAlgorithm.checksumAlgorithm_sha1)) {
					return this.checksums[i].getValue();
				}
			}
		}
		// No sha1 found, return an empty string
		return "";
	}
	
	/**
	 * Converts URI's for the different file types to file types
	 * @param uris
	 * @param ignoreErrors If true, any URI's that don't correspond to a know file type will not be included.  If true, an exception is thrown.
	 * @return
	 * @throws InvalidSPDXAnalysisException 
	 */
	private FileType[] urisToFileType(String[] uris, boolean ignoreErrors) throws InvalidSPDXAnalysisException {
		ArrayList<FileType> retval = new ArrayList<FileType>();
		for (int i = 0; i < uris.length; i++) {
			if (uris[i] != null && !uris[i].isEmpty()) {
				String fileTypeS = uris[i].substring(SpdxRdfConstants.SPDX_NAMESPACE.length());
				try {
					retval.add(FileType.valueOf(fileTypeS));
				} catch (Exception ex) {
					logger.error("Invalid file type in the model - "+fileTypeS);
					if (!ignoreErrors) {
						throw(new InvalidSPDXAnalysisException("Invalid file type: "+uris[i]));
					}
				}
			}
		}
		return retval.toArray(new FileType[retval.size()]);
	}
	
	private String[] fileTypesToUris(FileType[] fileTypes) {
		String[] retval = new String[fileTypes.length];
		for (int i = 0; i < retval.length; i++) {
			retval[i] = SpdxRdfConstants.SPDX_NAMESPACE + fileTypes[i].toString();
		}
		return retval;
	}


	@Override
	protected void populateModel() throws InvalidSPDXAnalysisException {
		super.populateModel();
		setPropertyUriValues(SpdxRdfConstants.SPDX_NAMESPACE, 
			SpdxRdfConstants.PROP_FILE_TYPE, 
			fileTypesToUris(this.fileTypes));
		setPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_CHECKSUM, this.checksums);		
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE,
			SpdxRdfConstants.PROP_FILE_CONTRIBUTOR, this.fileContributors);
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
			SpdxRdfConstants.PROP_FILE_NOTICE, noticeText);
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
			SpdxRdfConstants.PROP_FILE_ARTIFACTOF, artifactOf);
		// Add ID's to any file dependencies
		if (fileDependencies != null) {
			for (int i = 0; i < fileDependencies.length; i++) {
				if (fileDependencies[i].getId() == null) {
					fileDependencies[i].setId(modelContainer.getNextSpdxElementRef());
				}
			}
		}
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
			SpdxRdfConstants.PROP_FILE_FILE_DEPENDENCY, fileDependencies);
	}
	
	@Override
	protected String getLicenseInfoFromFilesPropertyName() {
		return SpdxRdfConstants.PROP_FILE_SEEN_LICENSE;
	}
	
	@Override
	protected String getNamePropertyName() {
		return SpdxRdfConstants.PROP_FILE_NAME;
	}

	/* (non-Javadoc)
	 * @see org.spdx.rdfparser.model.RdfModelObject#getType(com.hp.hpl.jena.rdf.model.Model)
	 */
	@Override
	Resource getType(Model model) {
		return model.createResource(SpdxRdfConstants.SPDX_NAMESPACE + SpdxRdfConstants.CLASS_SPDX_FILE);
	}
	
	/**
	 * @return the fileType
	 */
	public FileType[] getFileTypes() {
		if (this.resource != null && this.refreshOnGet) {
			String[] fileTypeUris = findUriPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE, 
					SpdxRdfConstants.PROP_FILE_TYPE);
			try {
				this.fileTypes = urisToFileType(fileTypeUris, true);
			} catch (InvalidSPDXAnalysisException e) {
				// ignore the error
			} 
		}
		return fileTypes;
	}

	/**
	 * @param fileTypes the fileTypes to set
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void setFileTypes(FileType[] fileTypes) throws InvalidSPDXAnalysisException {
		this.fileTypes = fileTypes;
		if (this.fileTypes == null) {
			this.fileTypes = new FileType[0];
		}
		setPropertyUriValues(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_TYPE, 
				fileTypesToUris(this.fileTypes));
	}

	/**
	 * @return the checksums
	 */
	public Checksum[] getChecksums() {
		if (this.resource != null && this.refreshOnGet) {
			try {
				Checksum[] refresh = findMultipleChecksumPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE, 
						SpdxRdfConstants.PROP_FILE_CHECKSUM);
				if (refresh == null || !this.arraysEquivalent(refresh, this.checksums)) {
					this.checksums = refresh;
				}
			} catch (InvalidSPDXAnalysisException e) {
				logger.error("Invalid checksum in model");
			}
		}
		return checksums;
	}

	/**
	 * @param checksums the checksums to set
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void setChecksums(Checksum[] checksums) throws InvalidSPDXAnalysisException {
		this.checksums = checksums;
		setPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_CHECKSUM, this.checksums);
	}

	/**
	 * @return the fileContributors
	 */
	public String[] getFileContributors() {
		if (this.resource != null && this.refreshOnGet) {
			this.fileContributors = findMultiplePropertyValues(SpdxRdfConstants.SPDX_NAMESPACE,
					SpdxRdfConstants.PROP_FILE_CONTRIBUTOR);
		}
		return fileContributors;
	}

	/**
	 * @param fileContributors the fileContributors to set
	 */
	public void setFileContributors(String[] fileContributors) {	
		if (fileContributors == null) {
			this.fileContributors = new String[0];
		} else {
			this.fileContributors = fileContributors;
		}
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE,
				SpdxRdfConstants.PROP_FILE_CONTRIBUTOR, fileContributors);
	}

	/**
	 * @return the noticeText
	 */
	public String getNoticeText() {
		if (this.resource != null && this.refreshOnGet) {
			this.noticeText = findSinglePropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
					SpdxRdfConstants.PROP_FILE_NOTICE);
		}
		return noticeText;
	}

	/**
	 * @param noticeText the noticeText to set
	 */
	public void setNoticeText(String noticeText) {
		this.noticeText = noticeText;
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_NOTICE, noticeText);
	}
	
	/**
	 * @return the artifactOf
	 */
	public DoapProject[] getArtifactOf() {
		if (this.resource != null && this.refreshOnGet) {
			try {
				DoapProject[] refresh = findMultipleDoapPropertyValues(SpdxRdfConstants.SPDX_NAMESPACE,
						SpdxRdfConstants.PROP_FILE_ARTIFACTOF);
				if (refresh == null || !this.arraysEquivalent(refresh, this.artifactOf)) {
					this.artifactOf = refresh;
				}
			} catch (InvalidSPDXAnalysisException e) {
				logger.error("Invalid artifact of in the model");
			}
		}
		return artifactOf;
	}

	/**
	 * @param artifactOf the artifactOf to set
	 * @throws InvalidSPDXAnalysisException 
	 */
	public void setArtifactOf(DoapProject[] artifactOf) throws InvalidSPDXAnalysisException {
		if (artifactOf == null) {
			this.artifactOf = new DoapProject[0];
		} else {
			this.artifactOf = artifactOf;
		}
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_ARTIFACTOF, this.artifactOf);
	}

	/**
	 * This method should no longer be used.  The Relationship property should be used in its place.
	 * @return the fileDependencies
	 */
	@Deprecated
	public SpdxFile[] getFileDependencies() {
		if (this.resource != null && this.refreshOnGet) {
			try {
				SpdxElement[] fileDependencyElements = findMultipleElementPropertyValues(
						SpdxRdfConstants.SPDX_NAMESPACE, SpdxRdfConstants.PROP_FILE_FILE_DEPENDENCY);
				if (!arraysEquivalent(fileDependencyElements, this.fileDependencies)) {
					int count = 0;
					if (fileDependencyElements != null) {
						for (int i = 0; i < fileDependencyElements.length; i++) {
							if (fileDependencyElements[i] instanceof SpdxFile) {
								count++;
							}
						}
					}
					if (count > 0) {
						this.fileDependencies = new SpdxFile[count];
						int j = 0;
						for (int i = 0; i < fileDependencyElements.length; i++) {
							if (fileDependencyElements[i] instanceof SpdxFile) {
								this.fileDependencies[j++] = (SpdxFile)fileDependencyElements[i];
							}
						}
					}
				}
			} catch (InvalidSPDXAnalysisException ex) {
				logger.error("Error getting file dependencies",ex);
			}
		}
		return fileDependencies;
	}

	/**
	 * This method should no longer be used.  The Relationship property should be used in its place.
	 * @param fileDependencies the fileDependencies to set
	 * @throws InvalidSPDXAnalysisException 
	 */
	@Deprecated
	public void setFileDependencies(SpdxFile[] fileDependencies) throws InvalidSPDXAnalysisException {
		if (fileDependencies == null) {
			this.fileDependencies = new SpdxFile[0];
		} else {
			this.fileDependencies = fileDependencies;
		}		
		// add any needed IDs
		if (this.resource != null) {
			for (int i = 0; i < this.fileDependencies.length; i++) {
				if (this.fileDependencies[i].resource == null && 
						this.fileDependencies[i].getId() == null) {
					this.fileDependencies[i].setId(modelContainer.getNextSpdxElementRef());
				}
			}
		}		
		setPropertyValue(SpdxRdfConstants.SPDX_NAMESPACE, 
				SpdxRdfConstants.PROP_FILE_FILE_DEPENDENCY, this.fileDependencies);
	}

	@Override
	public boolean equivalent(RdfModelObject o) {
		if (!(o instanceof SpdxFile)) {
			return false;
		}
		SpdxFile comp = (SpdxFile)o;
		if (!super.equivalent(comp)) {
			return false;
		}
		// compare based on properties
		// Note: We don't compare the ID's since they may be different if they come
		// from different models
		return (arraysEquivalent(this.checksums, comp.getChecksums()) &&
				this.arraysEqual(this.fileTypes, comp.getFileTypes())&&
				arraysEqual(this.fileContributors, comp.getFileContributors()) &&
				arraysEquivalent(this.artifactOf, comp.getArtifactOf()) &&
				arraysEquivalent(this.fileDependencies, comp.getFileDependencies()) &&
				equalsConsideringNull(this.noticeText, comp.getNoticeText()));
	}
	
	protected Checksum[] cloneChecksum() {
		if (checksums == null) {
			return null;
		}
		Checksum[] retval = new Checksum[checksums.length];
		for (int i = 0; i < checksums.length; i++) {
			retval[i] = checksums[i].clone();
		}
		return retval;
	}
	
	protected DoapProject[] cloneArtifactOf() {
		if (this.artifactOf == null) {
			return null;
		}
		DoapProject[] retval = new DoapProject[this.artifactOf.length];
		for (int i = 0; i < this.artifactOf.length; i++) {
			retval[i] = artifactOf[i].clone();
		}
		return retval;
	}
	
	public SpdxFile[] cloneFileDependencies() {
		if (this.fileDependencies == null) {
			return null;
		}
		SpdxFile[] retval = new SpdxFile[this.fileDependencies.length];
		for (int i = 0; i < this.fileDependencies.length; i++) {
			retval[i] = this.fileDependencies[i].clone();
		}
		return retval;
	}
	
	@Override public SpdxFile clone() {
		// We will not clone ID since it is used to create the URI
		SpdxFile retval;
		try {
			retval = new SpdxFile(null, name, comment, cloneAnnotations(),
					cloneRelationships(), cloneLicenseConcluded(),
					cloneLicenseInfosFromFiles(), copyrightText,
					licenseComment, fileTypes, cloneChecksum(),
					fileContributors, noticeText, cloneArtifactOf());
		} catch (InvalidSPDXAnalysisException e) {
			logger.error("Error cloning file: ",e);
			retval = null;
		}

		if (this.fileDependencies != null) {
			try {
				retval.setFileDependencies(cloneFileDependencies());
			} catch (InvalidSPDXAnalysisException e1) {
				logger.warn("Error setting file dependencies on clone", e1);
			}
		}
		return retval;
	}
	
	@Override
	public ArrayList<String> verify() {
		ArrayList<String> retval = super.verify();
		String fileName = this.getName();
		if (fileName == null) {
			fileName = "UNKNOWN";
		}
		if (checksums == null || checksums.length == 0) {
			retval.add("Missing required checksum for file "+fileName);
		} else {
			for (int i = 0; i < checksums.length; i++) {
				retval.addAll(checksums[i].verify());
			}			
		}
		String sha1 = getSha1();
		if (sha1 == null || sha1.isEmpty()) {
			retval.add("Missing required SHA1 hashcode value for "+name);
		}
		DoapProject[] projects = this.getArtifactOf();
		if (projects != null) {
			for (int i = 0;i < projects.length; i++) {
				retval.addAll(projects[i].verify());
			}
		}	
		// fileDependencies
		if (fileDependencies != null) {
			for (int i = 0; i < fileDependencies.length; i++) {
				ArrayList<String> verifyFileDependency = fileDependencies[i].verify();
				for (int j = 0; j < verifyFileDependency.size(); j++) {
					retval.add("Invalid file dependency for file named "+
							fileDependencies[i].getName()+": "+verifyFileDependency.get(j));
				}
			}
		}
		return retval;
	}
	
    /**
     * This method is used for sorting a list of SPDX files
     * @param file SPDXFile that is compared
     * @return 
     */
    @Override
    public int compareTo(SpdxFile file) {
        return this.getName().compareTo(file.getName());        
    }

    // the following methods are added as a TEMPORARY convenience to those
    // migrating from the 1.2 version of the utilities
	/**
	 * This method should be replaced by the more consistent getCopyrightText
	 * This method will be removed in a future release
	 * @return
	 */
    @Deprecated
	public String getCopyright() {
		return this.getCopyrightText();
	}

    /**
	 * This method should be replaced by the more consistent setCopyrightText
	 * This method will be removed in a future release
     * @param copyright
     */
    @Deprecated
    public void setCopyright(String copyright) {
    	this.setCopyrightText(copyright);
    }

	/**
	 * This method should be replaced by the more consistent getLicenseComment (without the s)
	 * This method will be removed in a future release
	 * @return
	 */
    @Deprecated
	public String getLicenseComments() {
		return this.getLicenseComment();
	}
    
	/**
	 * This method should be replaced by the more consistent setLicenseComment (without the s)
	 * This method will be removed in a future release
	 */
    @Deprecated
	public void setLicenseComments(String licenseComment) {
		this.setLicenseComment(licenseComment);
	}

	/**
	 * This method should be replaced by the more consistent getLicenseConcluded
	 * This method will be removed in a future release
	 * @return
	 */
    @Deprecated
	public AnyLicenseInfo getConcludedLicenses() {
		return this.getLicenseConcluded();
	}
    
	/**
	 * This method should be replaced by the more consistent setLicenseConcluded
	 * This method will be removed in a future release
	 * @throws InvalidSPDXAnalysisException 
	 */
    @Deprecated
	public void setConcludedLicenses(AnyLicenseInfo concludedLicense) throws InvalidSPDXAnalysisException {
		this.setLicenseConcluded(concludedLicense);
	}

	/**
	 * This method should be replaced by the more consistent getFileContributors
	 * This method will be removed in a future release
	 * @return
	 */
    @Deprecated
	public String[] getContributors() {
		return this.getFileContributors();
	}
    
	/**
	 * This method should be replaced by the more consistent setFileContributors
	 * This method will be removed in a future release
	 * @return
	 */
    @Deprecated
	public void setContributors(String[] contributors) {
		this.setFileContributors(contributors);
	}

	/**
	 * This method should be replaced by the more consistent getLicenseInfoFromFiles
	 * This method will be removed in a future release
	 * @return
	 */
    @Deprecated
	public AnyLicenseInfo[] getSeenLicenses() {
		return this.getLicenseInfoFromFiles();
	}
    
	/**
	 * This method should be replaced by the more consistent setLicenseInfoFromFiles
	 * This method will be removed in a future release
	 * @throws InvalidSPDXAnalysisException 
	 */
    @Deprecated
	public void setSeenLicenses(AnyLicenseInfo[] seenLicenses) throws InvalidSPDXAnalysisException {
		this.setLicenseInfosFromFiles(seenLicenses);
	}
}

