package xyz.leutgeb.lorenz.lac.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardCopyOption;

public class Native {
  private static final int MIN_PREFIX_LENGTH = 3;

  public static final String NATIVE_FOLDER_PATH_PREFIX = "lac";

  private static File temporaryDir;

  private Native() {}

  /**
   * Loads library from current JAR archive
   *
   * <p>The file from JAR is copied into system temporary directory and then loaded. The temporary
   * file is deleted after exiting. Method uses String as filename because the pathname is
   * "abstract", not system-dependent.
   *
   * @param path The path of file inside JAR as.
   * @throws IOException If temporary file creation or read/write operation fails
   * @throws IllegalArgumentException If source file (param path) does not exist
   * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than
   *     three characters (restriction of {@link File#createTempFile(java.lang.String,
   *     java.lang.String)}).
   * @throws FileNotFoundException If the file could not be found inside the JAR.
   */
  public static void loadLibraryFromJar(Path path) throws IOException {
    // Obtain filename from path
    String filename = path.getFileName().toString();

    // Check if the filename is okay
    if (filename == null || filename.length() < MIN_PREFIX_LENGTH) {
      throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
    }

    // Prepare temporary file
    if (temporaryDir == null) {
      temporaryDir = createTempDirectory(NATIVE_FOLDER_PATH_PREFIX);
      temporaryDir.deleteOnExit();
    }

    File temp = new File(temporaryDir, filename);

    try (InputStream is = load(path.toString())) {
      Files.copy(is, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      temp.delete();
      throw e;
    } catch (NullPointerException e) {
      temp.delete();
      throw new FileNotFoundException("File " + path + " was not found inside JAR.");
    }

    try {
      System.load(temp.getAbsolutePath());
    } finally {
      if (isPosixCompliant()) {
        // Assume POSIX compliant file system, can be deleted after loading
        temp.delete();
      } else {
        // Assume non-POSIX, and don't delete until last file descriptor closed
        temp.deleteOnExit();
      }
    }
  }

  private static boolean isPosixCompliant() {
    try {
      return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    } catch (FileSystemNotFoundException | ProviderNotFoundException | SecurityException e) {
      return false;
    }
  }

  private static File createTempDirectory(String prefix) throws IOException {
    String tempDir = System.getProperty("java.io.tmpdir");
    File generatedDir = new File(tempDir, prefix + System.nanoTime());

    if (!generatedDir.mkdir())
      throw new IOException("Failed to create temp directory " + generatedDir.getName());

    return generatedDir;
  }

  private static InputStream load(String name) {
    final var direct = Native.class.getResourceAsStream(name);
    if (direct == null) {
      final var systemClassLoader = ClassLoader.getSystemClassLoader();
      if (systemClassLoader != null) {
        return systemClassLoader.getResourceAsStream(name);
      }
    }
    return null;
  }
}
