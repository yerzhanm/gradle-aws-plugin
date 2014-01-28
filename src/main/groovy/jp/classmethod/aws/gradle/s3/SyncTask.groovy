package jp.classmethod.aws.gradle.s3

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.TaskAction;

import com.amazonaws.services.s3.*
import com.amazonaws.services.s3.model.*

class SyncTask extends DefaultTask {
	
	String bucketName
	
	String prefix = ''
	
	File source
	
	@TaskAction
	def uploadAction() {
		if (! bucketName) throw new GradleException("bucketName is not specified")
		if (! source) throw new GradleException("source is not specified")
		
		String prefix = this.prefix
		prefix = prefix.startsWith('/') ? prefix.substring(1) : prefix
		
		upload(prefix)
		delete(prefix)
	}
	
	private String upload(String prefix) {
		AmazonS3PluginExtension ext = project.extensions.getByType(AmazonS3PluginExtension)
		AmazonS3 s3 = ext.s3
		
		println "uploading... ${source} to s3://${bucketName}/${prefix}"
		project.fileTree(source).visit { FileTreeElement element ->
			if (element.isDirectory() == false) {
				String relativePath = prefix + element.relativePath.toString()
				String key = relativePath.startsWith('/') ? relativePath.substring(1) : relativePath
				
				String md5
				FileInputStream fis = null
				try {
					fis = new FileInputStream(element.file)
					md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis);
				} finally {
					if (fis != null) {
						fis.close()
					}
				}
				
				boolean doUpload = false
				try {
					def metadata = s3.getObjectMetadata(bucketName, key)
					if (metadata.ETag.equalsIgnoreCase(md5) == false) {
						doUpload = true
					}
				} catch (AmazonS3Exception e) {
					doUpload = true
				}
				
				if (doUpload) {
					println " => s3://${bucketName}/${key}"
					s3.putObject(bucketName, key, element.file)
				} else {
					println " => s3://${bucketName}/${key} (SKIP)"
				}
			}
		}
	}
	
	private delete(String prefix) {
		AmazonS3PluginExtension ext = project.extensions.getByType(AmazonS3PluginExtension)
		AmazonS3 s3 = ext.s3
		
		String pathPrefix = source.toString()
		pathPrefix += pathPrefix.endsWith('/') ? '' : '/'
		s3.listObjects(bucketName, prefix).objectSummaries.each { S3ObjectSummary os ->
			def File f = project.file(pathPrefix + os.key.substring(prefix.length()))
			if (f.exists() == false) {
				println "deleting... s3://${bucketName}/${os.key}"
				s3.deleteObject(bucketName, os.key)
			}
		}
	}
}
