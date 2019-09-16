package br.com.herio.arqmsmobile.infra.drive;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import br.com.herio.arqmsmobile.service.FileStorageService;

@Component
public class GoogleDriveFachada {
	// https://developers.google.com/identity/sign-in/web/sign-in#before_you_begin
	// https://developers.google.com/drive/api/v3/quickstart/java?authuser=1

	private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFachada.class);
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String TOKENS_DIRECTORY_PATH = "tokens";

	private static final Set<String> SCOPES = DriveScopes.all();
	private Drive service;

	@Value("${googledrive.appName}")
	private String appName;

	@Value("${googledrive.credentialsFile}")
	private String credentialsFile;

	@Autowired
	protected ImageResizer imageResizer;

	@Autowired
	protected FileStorageService fileStorageService;

	@PostConstruct
	public void init() {
		try {
			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			Credential credential = getCredentials(HTTP_TRANSPORT);
			if (credential != null) {
				this.service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
						.setApplicationName(appName)
						.build();
			}
		} catch (GeneralSecurityException | IOException e) {
			throw new RuntimeException("GoogleDriveFachada Erro ao conectar ao Drive", e);
		}
	}

	public List<File> listFiles(String idFolder, Long idUsuario) {
		try {
			String idFolderPesquisa = idFolder;
			if (idUsuario != null) {
				idFolderPesquisa = recuperaDiretorioUsuario(idFolder, idUsuario).getId();
			}
			return service.files().list()
					.setQ(String.format("'%s' in parents", idFolderPesquisa))
					.setSpaces("drive")
					.setFields("files(id, name, parents)")
					.execute().getFiles();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public File getFolder(String idFolder) {
		try {
			return service.files().get(idFolder).execute();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void uploadFile(Long idUsuario, MultipartFile mFile, String idFolder, File gFile, File gFileThumb) {
		try {
			File diretorioUsuario = recuperaDiretorioUsuario(idFolder, idUsuario);
			String mimeType = new Tika().detect(mFile.getOriginalFilename());

			//gFile
			java.io.File file = fileStorageService.storeFile(mFile);
			File fileMetadata = new File();
			fileMetadata.setName(file.getName());
			fileMetadata.setParents(Collections.singletonList(diretorioUsuario.getId()));
			FileContent mediaContent = new FileContent(mimeType, file);
			gFile = service.files().create(fileMetadata, mediaContent)
					.setFields("id, name, parents, webViewLink")
					.execute();

			//gFileThumb
			if (mimeType != null && mimeType.contains("image")) {
				// redimensiona e salva imagem
				java.io.File fileThumb = imageResizer.salvaLocaleRedimensiona(mFile, 30);
				File fileMetadataThumb = new File();
				fileMetadataThumb.setName(fileThumb.getName());
				fileMetadataThumb.setParents(Collections.singletonList(diretorioUsuario.getId()));
				FileContent mediaContentThumb = new FileContent(mimeType, fileThumb);
				gFileThumb = service.files().create(fileMetadataThumb, mediaContentThumb)
						.setFields("id, name, parents, webViewLink")
						.execute();
			}

		} catch (IOException e) {
			throw new RuntimeException("GoogleDriveFachada Erro em uploadFile", e);
		}
	}

	protected File recuperaDiretorioUsuario(String idFolder, Long idUsuario) throws IOException {
		File diretorioUsuario = null;
		String pageToken = null;
		do {
			FileList result = service.files()
					.list()
					.setQ(String.format("name='%s' and '%s' in parents", idUsuario.toString(), idFolder))
					.setSpaces("drive")
					.setFields("nextPageToken, files(id, name, parents)")
					.setPageToken(pageToken)
					.execute();
			if (result.getFiles() != null && result.getFiles().size() == 1) {
				// encontrou diret�rio usu�rio
				diretorioUsuario = result.getFiles().get(0);
				break;
			}
			pageToken = result.getNextPageToken();
		} while (pageToken != null);

		if (diretorioUsuario == null) {
			// n�o encontrou diret�rio usu�rio, ir� cri�-lo
			File fileMetadata = new File();
			fileMetadata.setName(idUsuario.toString());
			fileMetadata.setMimeType("application/vnd.google-apps.folder");
			fileMetadata.setParents(Collections.singletonList(idFolder));
			diretorioUsuario = service.files()
					.create(fileMetadata)
					.setFields("id")
					.execute();
		}
		return diretorioUsuario;
	}

	public java.io.File downloadFile(String fileId, String fileName) {
		try {
			java.io.File file = new java.io.File(fileName);
			FileOutputStream outputStream = new FileOutputStream(file);
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			service.files().get(fileId).executeMediaAndDownloadTo(byteArrayOutputStream);
			byteArrayOutputStream.writeTo(outputStream);
			return file;
		} catch (IOException e) {
			throw new RuntimeException("GoogleDriveFachada Erro em downloadFile", e);
		}
	}

	public boolean deleteFile(String fileId) {
		try {
			service.files().delete(fileId).execute();
			return true;
		} catch (IOException e) {
			throw new RuntimeException("GoogleDriveFachada Erro em deleteFile", e);
		}
	}

	private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
		InputStream in = GoogleDriveFachada.class.getResourceAsStream(credentialsFile);
		if (in == null) {
			LOGGER.debug("GoogleDriveFachada n�o iniciado. Resource not found: " + credentialsFile);
		} else {
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
					.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
					.build();
			return flow.loadCredential("user");
		}
		return null;
	}

	public static void main(String... args) {
		try {
			GoogleDriveFachada fachadaGoogleDrive = new GoogleDriveFachada();
			List<File> files = fachadaGoogleDrive.listFiles("6xW613ez3abtPfDiWahYnJ4E", null);
			if (files == null || files.isEmpty()) {
				System.out.println("No files found.");
			} else {
				System.out.println("Files:");
				for (File file : files) {
					System.out.printf("file[%s],[%s]\n", file.getName(), file.getId());
				}
			}

			String fileName = "AMODIREITO.png";
			// file to upload
			URL resource = GoogleDriveFachada.class.getResource("/static/publico/" + fileName);
			java.io.File file = new java.io.File(resource.getFile());

			// dados upload
			FileInputStream input = new FileInputStream(file);
			MultipartFile mfile = new MockMultipartFile("file", file.getName(), "text/plain", IOUtils.toByteArray(input));
			String folderUploads = "1qk7108N-6xW613ez3abtPfDiWahYnJ4E";
			File fileUpload = null;
			File fileThumb = null;
			fachadaGoogleDrive.uploadFile(1L, mfile, folderUploads, fileUpload, fileThumb);
			if (fileUpload != null) {
				System.out.println(String.format("fileUpload[%s],[%s], [%s]", fileUpload.getName(),
						fileUpload.getId(), fileUpload.getParents()));

				java.io.File fileDownload = fachadaGoogleDrive.downloadFile(fileUpload.getId(), "imagem.png");
				if (fileDownload != null) {
					System.out.println(String.format("fileDownload[%s]", fileDownload.getName()));
				} else {
					System.out.println("fileDownload null");
				}
			} else {
				System.out.println("fileUpload null");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}