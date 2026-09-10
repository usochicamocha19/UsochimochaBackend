package com.app.usochicamochabackend.substation.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Almacena fotos de evidencia bajo {@code uploads/subestaciones/ejecuciones/{ejecucionId}/{uuid}.ext}.
 * A diferencia de los documentos de vehículo, aquí no hay semántica de "reemplazar la actual":
 * cada foto subida es un archivo nuevo e independiente (1..N por ejecución).
 */
@Service
public class EvidenciaStorageService {

    private static final long MAX_BYTES = 15 * 1024 * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadsRoot;

    public EvidenciaStorageService(@Value("${app.storage.uploads-root:uploads}") String uploadsRootProperty) {
        this.uploadsRoot = Paths.get(uploadsRootProperty).toAbsolutePath().normalize();
    }

    public record StoredFile(String rutaRelativa, String nombreOriginal) {
    }

    public StoredFile store(MultipartFile file, Long ejecucionId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Archivo vacío.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo supera el tamaño máximo permitido (15 MB).");
        }
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_TYPES.contains(mime)) {
            throw new IllegalArgumentException("Tipo de archivo no permitido. Use JPEG, PNG o WebP.");
        }

        Path dir = uploadsRoot.resolve("subestaciones").resolve("ejecuciones").resolve(String.valueOf(ejecucionId));
        Files.createDirectories(dir);

        String ext = resolveExtension(file.getOriginalFilename(), mime);
        String fileName = UUID.randomUUID() + ext;
        Path destino = dir.resolve(fileName);

        Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

        String rutaRelativa = uploadsRoot.relativize(destino).toString().replace('\\', '/');
        String nombreOriginal = file.getOriginalFilename() != null ? file.getOriginalFilename() : fileName;
        return new StoredFile(rutaRelativa, nombreOriginal);
    }

    private static String resolveExtension(String originalFilename, String mime) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        return switch (mime) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
