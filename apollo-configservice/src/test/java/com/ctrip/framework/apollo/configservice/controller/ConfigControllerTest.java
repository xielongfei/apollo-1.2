package com.ctrip.framework.apollo.configservice.controller;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.ctrip.framework.apollo.biz.entity.Release;
import com.ctrip.framework.apollo.biz.grayReleaseRule.GrayReleaseRulesHolder;
import com.ctrip.framework.apollo.biz.service.AppNamespaceService;
import com.ctrip.framework.apollo.biz.service.ReleaseService;
import com.ctrip.framework.apollo.common.entity.AppNamespace;
import com.ctrip.framework.apollo.configservice.util.InstanceConfigAuditUtil;
import com.ctrip.framework.apollo.configservice.util.NamespaceUtil;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.dto.ApolloConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Jason Song(song_s@ctrip.com)
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigControllerTest {
  private ConfigController configController;
  @Mock
  private ReleaseService releaseService;
  @Mock
  private AppNamespaceService appNamespaceService;
  private String someAppId;
  private String someClusterName;
  private String defaultClusterName;
  private String defaultNamespaceName;
  private String somePublicNamespaceName;
  private String someDataCenter;
  private String someClientIp;
  @Mock
  private Release someRelease;
  @Mock
  private Release somePublicRelease;
  @Mock
  private NamespaceUtil namespaceUtil;
  @Mock
  private InstanceConfigAuditUtil instanceConfigAuditUtil;
  @Mock
  private GrayReleaseRulesHolder grayReleaseRulesHolder;
  @Mock
  private HttpServletRequest someRequest;

  @Before
  public void setUp() throws Exception {
    configController = new ConfigController();
    ReflectionTestUtils.setField(configController, "releaseService", releaseService);
    ReflectionTestUtils.setField(configController, "appNamespaceService", appNamespaceService);
    ReflectionTestUtils.setField(configController, "namespaceUtil", namespaceUtil);
    ReflectionTestUtils.setField(configController, "instanceConfigAuditUtil", instanceConfigAuditUtil);
    ReflectionTestUtils.setField(configController, "grayReleaseRulesHolder", grayReleaseRulesHolder);

    someAppId = "1";
    someClusterName = "someClusterName";
    defaultClusterName = ConfigConsts.CLUSTER_NAME_DEFAULT;
    defaultNamespaceName = ConfigConsts.NAMESPACE_APPLICATION;
    somePublicNamespaceName = "somePublicNamespace";
    someDataCenter = "someDC";
    someClientIp = "someClientIp";
    String someValidConfiguration = "{\"apollo.bar\": \"foo\"}";
    String somePublicConfiguration = "{\"apollo.public.bar\": \"foo\"}";

    when(someRelease.getAppId()).thenReturn(someAppId);
    when(someRelease.getClusterName()).thenReturn(someClusterName);
    when(someRelease.getConfigurations()).thenReturn(someValidConfiguration);
    when(somePublicRelease.getConfigurations()).thenReturn(somePublicConfiguration);
    when(namespaceUtil.filterNamespaceName(defaultNamespaceName)).thenReturn(defaultNamespaceName);
    when(namespaceUtil.filterNamespaceName(somePublicNamespaceName))
        .thenReturn(somePublicNamespaceName);
    when(grayReleaseRulesHolder.findReleaseIdFromGrayReleaseRule(anyString(), anyString(),
        anyString(), anyString(), anyString())).thenReturn(null);
  }

  @Test
  public void testQueryConfig() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideNewReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideNewReleaseKey);
    when(someRelease.getNamespaceName()).thenReturn(defaultNamespaceName);

    ApolloConfig result = configController.queryConfig(someAppId, someClusterName,
        defaultNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, times(1)).findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName);
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(defaultNamespaceName, result.getNamespaceName());
    assertEquals(someServerSideNewReleaseKey, result.getReleaseKey());
    verify(instanceConfigAuditUtil, times(1)).audit(someAppId, someClusterName, someDataCenter,
        someClientIp, someAppId, someClusterName, defaultNamespaceName, someServerSideNewReleaseKey);
  }

  @Test
  public void testQueryConfigWithGrayRelease() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideNewReleaseKey = "2";
    String someServerSideGrayReleaseKey = "3";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    Release grayRelease = mock(Release.class);
    long grayReleaseId = 999;
    String someGrayConfiguration = "{\"apollo.bar\": \"foo_gray\"}";

    when(grayReleaseRulesHolder.findReleaseIdFromGrayReleaseRule(someAppId, someClientIp,
        someAppId, someClusterName, defaultNamespaceName)).thenReturn(grayReleaseId);
    when(releaseService.findActiveOne(grayReleaseId)).thenReturn(grayRelease);
    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName))
        .thenReturn(someRelease);

    when(someRelease.getReleaseKey()).thenReturn(someServerSideNewReleaseKey);

    when(grayRelease.getAppId()).thenReturn(someAppId);
    when(grayRelease.getClusterName()).thenReturn(someClusterName);
    when(grayRelease.getReleaseKey()).thenReturn(someServerSideGrayReleaseKey);
    when(grayRelease.getNamespaceName()).thenReturn(defaultNamespaceName);
    when(grayRelease.getConfigurations()).thenReturn(someGrayConfiguration);

    ApolloConfig result = configController.queryConfig(someAppId, someClusterName,
        defaultNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, times(1)).findActiveOne(grayReleaseId);
    verify(releaseService, never()).findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName);

    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(defaultNamespaceName, result.getNamespaceName());
    assertEquals(someServerSideGrayReleaseKey, result.getReleaseKey());
    verify(instanceConfigAuditUtil, times(1)).audit(someAppId, someClusterName, someDataCenter,
        someClientIp, someAppId, someClusterName, defaultNamespaceName, someServerSideGrayReleaseKey);
  }

  @Test
  public void testQueryConfigFile() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideNewReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String someNamespaceName = String.format("%s.%s", defaultClusterName, "properties");

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideNewReleaseKey);
    when(namespaceUtil.filterNamespaceName(someNamespaceName)).thenReturn(defaultNamespaceName);

    ApolloConfig result = configController.queryConfig(someAppId, someClusterName,
        someNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, times(1)).findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName);
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(someNamespaceName, result.getNamespaceName());
    assertEquals(someServerSideNewReleaseKey, result.getReleaseKey());
  }

  @Test
  public void testQueryConfigFileWithPrivateNamespace() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideNewReleaseKey = "2";
    String somePrivateNamespace = "datasource";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String somePrivateNamespaceName = String.format("%s.%s", somePrivateNamespace, "xml");
    AppNamespace appNamespace = mock(AppNamespace.class);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, somePrivateNamespace))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideNewReleaseKey);
    when(namespaceUtil.filterNamespaceName(somePrivateNamespaceName)).thenReturn(somePrivateNamespace);
    when(appNamespaceService.findOne(someAppId, somePrivateNamespace))
        .thenReturn(appNamespace);

    ApolloConfig result = configController.queryConfig(someAppId, someClusterName,
        somePrivateNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(somePrivateNamespaceName, result.getNamespaceName());
    assertEquals(someServerSideNewReleaseKey, result.getReleaseKey());
  }

  @Test
  public void testQueryConfigWithReleaseNotFound() throws Exception {
    String someClientSideReleaseKey = "1";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName))
        .thenReturn(null);

    ApolloConfig result = configController.queryConfig(someAppId, someClusterName,
        defaultNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    assertNull(result);
    verify(someResponse, times(1)).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  @Test
  public void testQueryConfigWithApolloConfigNotModified() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideReleaseKey = someClientSideReleaseKey;
    HttpServletResponse someResponse = mock(HttpServletResponse.class);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, defaultNamespaceName))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideReleaseKey);

    ApolloConfig
        result =
        configController.queryConfig(someAppId, someClusterName, defaultNamespaceName,
            someDataCenter, String.valueOf(someClientSideReleaseKey), someClientIp, someRequest, someResponse);

    assertNull(result);
    verify(someResponse, times(1)).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
  }

  @Test
  public void testQueryConfigWithDefaultClusterWithDataCenterRelease() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideNewReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);

    when(releaseService.findLatestActiveRelease(someAppId, someDataCenter, defaultNamespaceName))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideNewReleaseKey);
    when(someRelease.getClusterName()).thenReturn(someDataCenter);

    ApolloConfig result = configController.queryConfig(someAppId, defaultClusterName,
        defaultNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, times(1)).findLatestActiveRelease(someAppId, someDataCenter, defaultNamespaceName);
    assertEquals(someAppId, result.getAppId());
    assertEquals(someDataCenter, result.getCluster());
    assertEquals(defaultNamespaceName, result.getNamespaceName());
    assertEquals(someServerSideNewReleaseKey, result.getReleaseKey());
  }

  @Test
  public void testQueryConfigWithDefaultClusterWithNoDataCenterRelease() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideNewReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);

    when(releaseService.findLatestActiveRelease(someAppId, someDataCenter, defaultNamespaceName))
        .thenReturn(null);
    when(releaseService.findLatestActiveRelease(someAppId, defaultClusterName, defaultNamespaceName))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideNewReleaseKey);
    when(someRelease.getClusterName()).thenReturn(defaultClusterName);

    ApolloConfig result = configController.queryConfig(someAppId, defaultClusterName,
        defaultNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, times(1)).findLatestActiveRelease(someAppId, someDataCenter, defaultNamespaceName);
    verify(releaseService, times(1))
        .findLatestActiveRelease(someAppId, defaultClusterName, defaultNamespaceName);
    assertEquals(someAppId, result.getAppId());
    assertEquals(defaultClusterName, result.getCluster());
    assertEquals(defaultNamespaceName, result.getNamespaceName());
    assertEquals(someServerSideNewReleaseKey, result.getReleaseKey());
  }

  @Test
  public void testQueryConfigWithAppOwnNamespace() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideReleaseKey = "2";
    String someAppOwnNamespaceName = "someAppOwn";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    AppNamespace someAppOwnNamespace =
        assemblePublicAppNamespace(someAppId, someAppOwnNamespaceName);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, someAppOwnNamespaceName))
        .thenReturn(someRelease);
    when(appNamespaceService.findPublicNamespaceByName(someAppOwnNamespaceName))
        .thenReturn(someAppOwnNamespace);
    when(someRelease.getReleaseKey()).thenReturn(someServerSideReleaseKey);
    when(namespaceUtil.filterNamespaceName(someAppOwnNamespaceName))
        .thenReturn(someAppOwnNamespaceName);

    ApolloConfig result =
        configController
            .queryConfig(someAppId, someClusterName, someAppOwnNamespaceName, someDataCenter,
                someClientSideReleaseKey, someClientIp, someRequest, someResponse);

    assertEquals(someServerSideReleaseKey, result.getReleaseKey());
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(someAppOwnNamespaceName, result.getNamespaceName());
    assertEquals("foo", result.getConfigurations().get("apollo.bar"));
  }

  @Test
  public void testQueryConfigWithPubicNamespaceAndNoAppOverride() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String somePublicAppId = "somePublicAppId";
    String somePublicClusterName = "somePublicClusterName";
    AppNamespace somePublicAppNamespace =
        assemblePublicAppNamespace(somePublicAppId, somePublicNamespaceName);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, somePublicNamespaceName))
        .thenReturn(null);
    when(appNamespaceService.findPublicNamespaceByName(somePublicNamespaceName))
        .thenReturn(somePublicAppNamespace);
    when(releaseService.findLatestActiveRelease(somePublicAppId, someDataCenter, somePublicNamespaceName))
        .thenReturn(somePublicRelease);
    when(somePublicRelease.getReleaseKey()).thenReturn(someServerSideReleaseKey);
    when(somePublicRelease.getAppId()).thenReturn(somePublicAppId);
    when(somePublicRelease.getClusterName()).thenReturn(somePublicClusterName);
    when(somePublicRelease.getNamespaceName()).thenReturn(somePublicNamespaceName);

    ApolloConfig result = configController
        .queryConfig(someAppId, someClusterName, somePublicNamespaceName, someDataCenter,
            someClientSideReleaseKey, someClientIp, someRequest, someResponse);

    assertEquals(someServerSideReleaseKey, result.getReleaseKey());
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(somePublicNamespaceName, result.getNamespaceName());
    assertEquals("foo", result.getConfigurations().get("apollo.public.bar"));
    verify(instanceConfigAuditUtil, times(1)).audit(someAppId, someClusterName, someDataCenter,
        someClientIp, somePublicAppId, somePublicClusterName, somePublicNamespaceName, someServerSideReleaseKey);
  }

  @Test
  public void testQueryConfigFileWithPublicNamespaceAndNoAppOverride() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String somePublicAppId = "somePublicAppId";
    String someNamespace = String.format("%s.%s", somePublicNamespaceName, "properties");
    AppNamespace somePublicAppNamespace =
        assemblePublicAppNamespace(somePublicAppId, somePublicNamespaceName);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, somePublicNamespaceName))
        .thenReturn(null);
    when(appNamespaceService.findPublicNamespaceByName(somePublicNamespaceName))
        .thenReturn(somePublicAppNamespace);
    when(releaseService.findLatestActiveRelease(somePublicAppId, someDataCenter, somePublicNamespaceName))
        .thenReturn(somePublicRelease);
    when(somePublicRelease.getReleaseKey()).thenReturn(someServerSideReleaseKey);
    when(namespaceUtil.filterNamespaceName(someNamespace)).thenReturn(somePublicNamespaceName);
    when(appNamespaceService.findOne(someAppId, somePublicNamespaceName)).thenReturn(null);

    ApolloConfig result = configController
        .queryConfig(someAppId, someClusterName, someNamespace, someDataCenter,
            someClientSideReleaseKey, someClientIp, someRequest, someResponse);

    assertEquals(someServerSideReleaseKey, result.getReleaseKey());
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(someNamespace, result.getNamespaceName());
    assertEquals("foo", result.getConfigurations().get("apollo.public.bar"));
  }

  @Test
  public void testQueryConfigWithPublicNamespaceAndNoAppOverrideAndNoDataCenter() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String somePublicAppId = "somePublicAppId";
    AppNamespace somePublicAppNamespace =
        assemblePublicAppNamespace(somePublicAppId, somePublicNamespaceName);

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, somePublicNamespaceName))
        .thenReturn(null);
    when(appNamespaceService.findPublicNamespaceByName(somePublicNamespaceName))
        .thenReturn(somePublicAppNamespace);
    when(releaseService.findLatestActiveRelease(somePublicAppId, someDataCenter, somePublicNamespaceName))
        .thenReturn(null);
    when(releaseService
        .findLatestActiveRelease(somePublicAppId, ConfigConsts.CLUSTER_NAME_DEFAULT, somePublicNamespaceName))
        .thenReturn(somePublicRelease);
    when(somePublicRelease.getReleaseKey()).thenReturn(someServerSideReleaseKey);

    ApolloConfig result =
        configController
            .queryConfig(someAppId, someClusterName, somePublicNamespaceName, someDataCenter,
                someClientSideReleaseKey, someClientIp, someRequest, someResponse);

    assertEquals(someServerSideReleaseKey, result.getReleaseKey());
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(somePublicNamespaceName, result.getNamespaceName());
    assertEquals("foo", result.getConfigurations().get("apollo.public.bar"));
  }

  @Test
  public void testQueryConfigWithPublicNamespaceAndAppOverride() throws Exception {
    String someAppSideReleaseKey = "1";
    String somePublicAppSideReleaseKey = "2";

    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String somePublicAppId = "somePublicAppId";
    AppNamespace somePublicAppNamespace =
        assemblePublicAppNamespace(somePublicAppId, somePublicNamespaceName);

    when(someRelease.getConfigurations()).thenReturn("{\"apollo.public.foo\": \"foo-override\"}");
    when(somePublicRelease.getConfigurations())
        .thenReturn("{\"apollo.public.foo\": \"foo\", \"apollo.public.bar\": \"bar\"}");

    when(releaseService.findLatestActiveRelease(someAppId, someClusterName, somePublicNamespaceName))
        .thenReturn(someRelease);
    when(someRelease.getReleaseKey()).thenReturn(someAppSideReleaseKey);
    when(someRelease.getNamespaceName()).thenReturn(somePublicNamespaceName);
    when(appNamespaceService.findPublicNamespaceByName(somePublicNamespaceName))
        .thenReturn(somePublicAppNamespace);
    when(releaseService.findLatestActiveRelease(somePublicAppId, someDataCenter, somePublicNamespaceName))
        .thenReturn(somePublicRelease);
    when(somePublicRelease.getReleaseKey()).thenReturn(somePublicAppSideReleaseKey);
    when(somePublicRelease.getAppId()).thenReturn(somePublicAppId);
    when(somePublicRelease.getClusterName()).thenReturn(someDataCenter);
    when(somePublicRelease.getNamespaceName()).thenReturn(somePublicNamespaceName);

    ApolloConfig result =
        configController
            .queryConfig(someAppId, someClusterName, somePublicNamespaceName, someDataCenter,
                someAppSideReleaseKey, someClientIp, someRequest, someResponse);

    assertEquals(Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
            .join(someAppSideReleaseKey, somePublicAppSideReleaseKey),
        result.getReleaseKey());
    assertEquals(someAppId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(somePublicNamespaceName, result.getNamespaceName());
    assertEquals("foo-override", result.getConfigurations().get("apollo.public.foo"));
    assertEquals("bar", result.getConfigurations().get("apollo.public.bar"));
    verify(instanceConfigAuditUtil, times(1)).audit(someAppId, someClusterName, someDataCenter,
        someClientIp, someAppId, someClusterName, somePublicNamespaceName, someAppSideReleaseKey);
    verify(instanceConfigAuditUtil, times(1)).audit(someAppId, someClusterName, someDataCenter,
        someClientIp, somePublicAppId, someDataCenter, somePublicNamespaceName, somePublicAppSideReleaseKey);
  }

  @Test
  public void testMergeConfigurations() throws Exception {
    Gson gson = new Gson();
    String key1 = "key1";
    String value1 = "value1";
    String anotherValue1 = "anotherValue1";

    String key2 = "key2";
    String value2 = "value2";

    Map<String, String> config = ImmutableMap.of(key1, anotherValue1);
    Map<String, String> anotherConfig = ImmutableMap.of(key1, value1, key2, value2);

    Release releaseWithHighPriority = new Release();
    releaseWithHighPriority.setConfigurations(gson.toJson(config));

    Release releaseWithLowPriority = new Release();
    releaseWithLowPriority.setConfigurations(gson.toJson(anotherConfig));

    Map<String, String> result =
        configController.mergeReleaseConfigurations(
            Lists.newArrayList(releaseWithHighPriority, releaseWithLowPriority));

    assertEquals(2, result.keySet().size());
    assertEquals(anotherValue1, result.get(key1));
    assertEquals(value2, result.get(key2));
  }

  @Test(expected = JsonSyntaxException.class)
  public void testTransformConfigurationToMapFailed() throws Exception {
    String someInvalidConfiguration = "xxx";
    Release someRelease = new Release();
    someRelease.setConfigurations(someInvalidConfiguration);

    configController.mergeReleaseConfigurations(Lists.newArrayList(someRelease));
  }

  @Test
  public void testQueryConfigForNoAppIdPlaceHolder() throws Exception {
    String someClientSideReleaseKey = "1";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String appId = ConfigConsts.NO_APPID_PLACEHOLDER;

    ApolloConfig result = configController.queryConfig(appId, someClusterName,
        defaultNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, never()).findLatestActiveRelease(appId, someClusterName, defaultNamespaceName);
    verify(appNamespaceService, never()).findPublicNamespaceByName(defaultNamespaceName);
    assertNull(result);
    verify(someResponse, times(1)).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
  }

  @Test
  public void testQueryConfigForNoAppIdPlaceHolderWithPublicNamespace() throws Exception {
    String someClientSideReleaseKey = "1";
    String someServerSideReleaseKey = "2";
    HttpServletResponse someResponse = mock(HttpServletResponse.class);
    String somePublicAppId = "somePublicAppId";
    AppNamespace somePublicAppNamespace =
        assemblePublicAppNamespace(somePublicAppId, somePublicNamespaceName);
    String appId = ConfigConsts.NO_APPID_PLACEHOLDER;

    when(appNamespaceService.findPublicNamespaceByName(somePublicNamespaceName))
        .thenReturn(somePublicAppNamespace);
    when(releaseService.findLatestActiveRelease(somePublicAppId, someDataCenter, somePublicNamespaceName))
        .thenReturn(somePublicRelease);
    when(somePublicRelease.getReleaseKey()).thenReturn(someServerSideReleaseKey);

    ApolloConfig result = configController.queryConfig(appId, someClusterName,
        somePublicNamespaceName, someDataCenter, someClientSideReleaseKey,
        someClientIp, someRequest, someResponse);

    verify(releaseService, never()).findLatestActiveRelease(appId, someClusterName, somePublicNamespaceName);
    assertEquals(someServerSideReleaseKey, result.getReleaseKey());
    assertEquals(appId, result.getAppId());
    assertEquals(someClusterName, result.getCluster());
    assertEquals(somePublicNamespaceName, result.getNamespaceName());
    assertEquals("foo", result.getConfigurations().get("apollo.public.bar"));
  }

  private AppNamespace assemblePublicAppNamespace(String appId, String namespace) {
    return assembleAppNamespace(appId, namespace, true);
  }

  private AppNamespace assembleAppNamespace(String appId, String namespace, boolean isPublic) {
    AppNamespace appNamespace = new AppNamespace();
    appNamespace.setAppId(appId);
    appNamespace.setName(namespace);
    appNamespace.setPublic(isPublic);
    return appNamespace;
  }
}
