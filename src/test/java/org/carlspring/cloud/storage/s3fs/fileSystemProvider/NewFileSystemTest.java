package org.carlspring.cloud.storage.s3fs.fileSystemProvider;

import org.carlspring.cloud.storage.s3fs.S3FileSystem;
import org.carlspring.cloud.storage.s3fs.S3FileSystemConfigurationException;
import org.carlspring.cloud.storage.s3fs.S3FileSystemProvider;
import org.carlspring.cloud.storage.s3fs.S3UnitTestBase;
import org.carlspring.cloud.storage.s3fs.util.Constants;
import org.carlspring.cloud.storage.s3fs.util.S3EndpointConstant;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.carlspring.cloud.storage.s3fs.S3Factory.ACCESS_KEY;
import static org.carlspring.cloud.storage.s3fs.S3Factory.SECRET_KEY;
import static org.carlspring.cloud.storage.s3fs.S3FileSystemProvider.CHARSET_KEY;
import static org.carlspring.cloud.storage.s3fs.S3FileSystemProvider.S3_FACTORY_CLASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

class NewFileSystemTest
        extends S3UnitTestBase
{


    @BeforeEach
    public void setup()
            throws IOException
    {
        s3fsProvider = getS3fsProvider();
        fileSystem = s3fsProvider.newFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST, null);
    }

    @Test
    void misconfigure()
    {
        Properties props = new Properties();
        props.setProperty(S3_FACTORY_CLASS, "org.carlspring.cloud.storage.s3fs.util.BrokenS3Factory");

        // We're expecting an exception here to be thrown
        Exception exception = assertThrows(S3FileSystemConfigurationException.class,
                                           () -> s3fsProvider.createFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST,
                                                                               props));

        assertNotNull(exception);
    }

    @Test
    void newS3FileSystemWithEmptyHostAndUserInfo()
    {
        FileSystem s3fs = s3fsProvider.newFileSystem(URI.create("s3:///bucket/file"),
                                                     ImmutableMap.of());

        assertEquals(Constants.S3_HOSTNAME, ((S3FileSystem) s3fs).getKey());
    }

    @Test
    void newS3FileSystemWithEmptyHost()
    {
        FileSystem s3fs = s3fsProvider.newFileSystem(URI.create("s3://access-key:secret-key@/bucket/file"),
                                                     ImmutableMap.of());

        assertEquals("access-key:secret-key@" + Constants.S3_HOSTNAME, ((S3FileSystem) s3fs).getKey());
    }

    @Test
    void newS3FileSystemWithCustomHost()
    {
        FileSystem s3fs = s3fsProvider.newFileSystem(URI.create("s3://access-key:secret-key@my.ceph.storage"),
                                                     ImmutableMap.of());

        assertEquals("access-key:secret-key@my.ceph.storage", ((S3FileSystem) s3fs).getKey());
    }

    @Test
    void newS3FileSystemWithCustomHostAndBucket()
    {
        FileSystem s3fs = s3fsProvider.newFileSystem(URI.create("s3://access-key:secret-key@my.ceph.storage/bucket"),
                                                     ImmutableMap.of());

        assertEquals("access-key:secret-key@my.ceph.storage", ((S3FileSystem) s3fs).getKey());
    }

    @Test
    void createsAuthenticatedByEnv()
    {
        Map<String, ?> env = buildFakeEnv();

        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        FileSystem fileSystem = s3fsProvider.newFileSystem(uri, env);

        assertNotNull(fileSystem);

        verify(s3fsProvider).createFileSystem(eq(uri),
                                              eq(buildFakeProps((String) env.get(ACCESS_KEY),
                                                                (String) env.get(SECRET_KEY))));
    }

    @Test
    void setEncodingByProperties()
    {
        Properties props = new Properties();
        props.setProperty(SECRET_KEY, "better_secret_key");
        props.setProperty(ACCESS_KEY, "better_access_key");
        props.setProperty(CHARSET_KEY, "UTF-8");

        doReturn(props).when(s3fsProvider).loadAmazonProperties();

        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        FileSystem fileSystem = s3fsProvider.newFileSystem(uri, ImmutableMap.of());

        assertNotNull(fileSystem);

        verify(s3fsProvider).createFileSystem(eq(uri),
                                              eq(buildFakeProps("better_access_key", "better_secret_key",
                                                                StandardCharsets.UTF_8.toString())));
    }

    @Test
    void createAuthenticatedByProperties()
    {
        Properties props = new Properties();
        props.setProperty(SECRET_KEY, "better_secret_key");
        props.setProperty(ACCESS_KEY, "better_access_key");

        doReturn(props).when(s3fsProvider).loadAmazonProperties();

        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        FileSystem fileSystem = s3fsProvider.newFileSystem(uri, ImmutableMap.of());

        assertNotNull(fileSystem);

        verify(s3fsProvider).createFileSystem(eq(uri), eq(buildFakeProps("better_access_key", "better_secret_key")));
    }

    @Test
    void createAuthenticatedBySystemEnvironment()
    {
        final String accessKey = "better-access-key";
        final String secretKey = "better-secret-key";

        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        doReturn(accessKey).when(s3fsProvider).systemGetEnv(ACCESS_KEY);
        doReturn(secretKey).when(s3fsProvider).systemGetEnv(SECRET_KEY);
        doCallRealMethod().when(s3fsProvider).overloadPropertiesWithSystemEnv(any(Properties.class), anyString());

        s3fsProvider.newFileSystem(uri, ImmutableMap.of());

        verify(s3fsProvider).createFileSystem(eq(uri), argThat(properties -> {
            assertEquals(accessKey, properties.getProperty(ACCESS_KEY));
            assertEquals(secretKey, properties.getProperty(SECRET_KEY));

            return true;
        }));
    }

    @Test
    void createsAnonymous()
    {
        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        FileSystem fileSystem = s3fsProvider.newFileSystem(uri, ImmutableMap.of());

        assertNotNull(fileSystem);

        verify(s3fsProvider).createFileSystem(eq(uri), eq(buildFakeProps(null, null)));
    }

    @Test
    void createWithDefaultEndpoint()
    {
        Properties props = new Properties();
        props.setProperty(SECRET_KEY, "better_secret_key");
        props.setProperty(ACCESS_KEY, "better_access_key");
        props.setProperty(CHARSET_KEY, "UTF-8");

        doReturn(props).when(s3fsProvider).loadAmazonProperties();

        URI uri = URI.create("s3:///");

        FileSystem fileSystem = s3fsProvider.newFileSystem(uri, ImmutableMap.of());

        assertNotNull(fileSystem);

        verify(s3fsProvider).createFileSystem(eq(uri),
                                              eq(buildFakeProps("better_access_key", "better_secret_key", "UTF-8")));
    }

    @Test
    void createWithOnlyAccessKey()
    {
        Properties props = new Properties();

        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        props.setProperty(ACCESS_KEY, "better_access_key");

        doReturn(props).when(s3fsProvider).loadAmazonProperties();

        final ImmutableMap<String, Object> env = ImmutableMap.of();

        // We're expecting an exception here to be thrown
        Exception exception = assertThrows(IllegalArgumentException.class,
                                           () -> s3fsProvider.newFileSystem(uri, env));

        assertNotNull(exception);
    }

    @Test
    void createWithOnlySecretKey()
    {
        Properties props = new Properties();
        props.setProperty(SECRET_KEY, "better_secret_key");

        doReturn(props).when(s3fsProvider).loadAmazonProperties();

        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        final ImmutableMap<String, Object> env = ImmutableMap.of();

        // We're expecting an exception here to be thrown
        Exception exception = assertThrows(IllegalArgumentException.class,
                                           () -> s3fsProvider.newFileSystem(uri, env));

        assertNotNull(exception);
    }

    @Test
    void createFailsIfAlreadyCreated()
    {
        final URI uri = URI.create("s3://" + UUID.randomUUID().toString());
        final ImmutableMap<String, Object> env = ImmutableMap.of();

        // We're expecting an exception here to be thrown
        final Exception exception = assertThrows(FileSystemAlreadyExistsException.class, () -> {

            final FileSystem fileSystem = s3fsProvider.newFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST, env);

            assertNotNull(fileSystem);

            s3fsProvider.newFileSystem(uri, env);
        });

        assertNotNull(exception);
    }

    @Test
    void createWithWrongEnv()
    {
        final Map<String, Object> env = ImmutableMap.<String, Object>builder().put(ACCESS_KEY, 1234)
                                                                        .put(SECRET_KEY, "secret key")
                                                                        .build();

        final URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        final ImmutableMap<String, Object> emptyEnv = ImmutableMap.of();

        // We're expecting an exception here to be thrown
        final Exception exception = assertThrows(IllegalArgumentException.class, () -> {

            final FileSystem fileSystem = s3fsProvider.newFileSystem(uri, env);

            assertNotNull(fileSystem);

            s3fsProvider.newFileSystem(uri, emptyEnv);
        });

        assertNotNull(exception);
    }

    @Test
    void getFileSystem()
    {
        URI uri = URI.create("s3://" + UUID.randomUUID().toString());

        FileSystem fileSystem = s3fsProvider.newFileSystem(uri, ImmutableMap.of());

        assertNotNull(fileSystem);

        fileSystem = s3fsProvider.getFileSystem(uri, ImmutableMap.of());

        assertNotNull(fileSystem);

        FileSystem other = s3fsProvider.getFileSystem(uri);

        assertSame(fileSystem, other);
    }

    @Test
    void getUnknownFileSystem()
    {
        FileSystem fileSystem = s3fsProvider.getFileSystem(URI.create("s3://endpoint20/bucket/path/to/file"),
                                                           ImmutableMap.of());

        assertNotNull(fileSystem);
    }

    @Test
    void closeFileSystemReturnNewFileSystem()
            throws IOException
    {
        S3FileSystemProvider provider = new S3FileSystemProvider();

        Map<String, ?> env = buildFakeEnv();

        FileSystem fileSystem = provider.newFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST, env);

        assertNotNull(fileSystem);

        fileSystem.close();

        FileSystem fileSystem2 = provider.newFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST, env);

        assertNotSame(fileSystem, fileSystem2);
    }

    @Test
    void createTwoFileSystemThrowError()
    {
        S3FileSystemProvider provider = new S3FileSystemProvider();

        Map<String, ?> env = buildFakeEnv();

        FileSystem fileSystem = provider.newFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST, env);

        assertNotNull(fileSystem);

        // We're expecting an exception here to be thrown
        Exception exception = assertThrows(FileSystemAlreadyExistsException.class,
                                           () -> provider.newFileSystem(S3EndpointConstant.S3_GLOBAL_URI_TEST, env));

        assertNotNull(exception);
    }


    private Map<String, ?> buildFakeEnv()
    {
        return ImmutableMap.<String, Object>builder().put(ACCESS_KEY, "access_key")
                                                     .put(SECRET_KEY, "secret_key")
                                                     .build();
    }

    private Properties buildFakeProps(String access_key,
                                      String secret_key,
                                      String encoding)
    {
        Properties props = buildFakeProps(access_key, secret_key);
        props.setProperty(CHARSET_KEY, encoding);

        return props;
    }


    private Properties buildFakeProps(String access_key,
                                      String secret_key)
    {
        Properties props = new Properties();
        props.setProperty(S3_FACTORY_CLASS, "org.carlspring.cloud.storage.s3fs.util.S3MockFactory");

        if (access_key != null)
        {
            props.setProperty(ACCESS_KEY, access_key);
        }

        if (secret_key != null)
        {
            props.setProperty(SECRET_KEY, secret_key);
        }

        return props;
    }

}
