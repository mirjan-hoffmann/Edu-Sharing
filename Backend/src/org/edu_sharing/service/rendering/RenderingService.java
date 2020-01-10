package org.edu_sharing.service.rendering;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.edu_sharing.service.InsufficientPermissionException;

public interface RenderingService {

    String getDetails(String nodeId, String nodeVersion, String displayMode, Map<String, String> parameters) throws InsufficientPermissionException, Exception;

    String getDetails(String renderingServiceUrl, RenderingServiceData data) throws JsonProcessingException, UnsupportedEncodingException;

    RenderingServiceData getData(String nodeId, String nodeVersion, String user, RenderingServiceOptions options) throws Exception;
}
