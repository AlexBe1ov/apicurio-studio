/*
 * Copyright 2017 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.hub.api.bitbucket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.ZipInputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;

import io.apicurio.hub.api.beans.BitbucketRepository;
import io.apicurio.hub.api.beans.BitbucketTeam;
import io.apicurio.hub.api.beans.ResourceContent;
import io.apicurio.hub.api.beans.SourceCodeBranch;
import io.apicurio.hub.api.connectors.AbstractSourceConnector;
import io.apicurio.hub.api.connectors.SourceConnectorException;
import io.apicurio.hub.core.beans.ApiDesignResourceInfo;
import io.apicurio.hub.core.beans.LinkedAccountType;
import io.apicurio.hub.core.config.HubConfiguration;
import io.apicurio.hub.core.exceptions.ApiValidationException;
import io.apicurio.hub.core.exceptions.NotFoundException;

/**
 * Implementation of the Bitbucket source connector.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class BitbucketSourceConnector extends AbstractSourceConnector implements IBitbucketSourceConnector {

    private static Logger logger = LoggerFactory.getLogger(BitbucketSourceConnector.class);

    private static final String BITBUCKET_API_ENDPOINT = "https://bitbucket.org/!api/2.0";
    protected static final Object TOKEN_TYPE_BASIC = "BASIC";
    protected static final Object TOKEN_TYPE_OAUTH = "OAUTH";

    @Inject
    private HubConfiguration config;
    
    /**
     * @see io.apicurio.hub.api.connectors.ISourceConnector#getType()
     */
    @Override
    public LinkedAccountType getType() {
        return LinkedAccountType.Bitbucket;
    }

    /**
     * @see AbstractSourceConnector#getBaseApiEndpointUrl()
     */
    @Override
    protected String getBaseApiEndpointUrl() {
        return BITBUCKET_API_ENDPOINT;
    }

    /**
     * @return the type of the external token (either private or oauth)
     */
    protected Object getExternalTokenType() {
        return TOKEN_TYPE_OAUTH;
    }

    /**
     * @see AbstractSourceConnector#parseExternalTokenResponse(String)
     */
    protected Map<String, String> parseExternalTokenResponse(String body) {
        try {
            Map<String, String> rval = new HashMap<>();
            JsonNode jsonNode = mapper.readTree(body);
            rval.put("access_token", jsonNode.get("access_token").asText());
            rval.put("token_type", jsonNode.get("token_type").asText());
            rval.put("refresh_token", jsonNode.get("refresh_token").asText());
            rval.put("scope", jsonNode.get("scopes").asText());
            rval.put("expires_in", jsonNode.get("expires_in").asText());
            return rval;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see io.apicurio.hub.api.connectors.ISourceConnector#validateResourceExists(String)
     */
    @Override
    public ApiDesignResourceInfo validateResourceExists(String repositoryUrl) throws NotFoundException, SourceConnectorException, ApiValidationException {
        logger.debug("Validating the existence of resource {}", repositoryUrl);
        try {
            BitbucketResource resource = BitbucketResourceResolver.resolve(repositoryUrl);
            if (resource == null) {
                throw new NotFoundException();
            }
            String content = getResourceContent(resource);

            ApiDesignResourceInfo info = ApiDesignResourceInfo.fromContent(content);
            if (info.getName() == null) {
                info.setName(resource.getResourcePath());
            }
            return info;
        } catch (NotFoundException nfe) {
            throw nfe;
        } catch (ApiValidationException ave) {
            throw ave;
        } catch (Exception e) {
            throw new SourceConnectorException("Error checking that a Bitbucket resource exists.", e);
        }
    }

    /**
     * Gets the content of the given Bitbucket resource.  This is done by querying for the
     * content using the GH API.
     *
     * @param resource
     */
    private String getResourceContent(BitbucketResource resource) throws NotFoundException, SourceConnectorException {
        logger.debug("Getting resource content for: {}/{} - {}",
                resource.getTeam(), resource.getRepository(), resource.getResourcePath());

        ResourceContent content = getResourceContentFromBitbucket(resource);

        return content.getContent();
    }

    /**
     * @see io.apicurio.hub.api.connectors.ISourceConnector#getResourceContent(String)
     */
    @Override
    public ResourceContent getResourceContent(String repositoryUrl) throws NotFoundException, SourceConnectorException {
        BitbucketResource resource = BitbucketResourceResolver.resolve(repositoryUrl);
        return getResourceContentFromBitbucket(resource);
    }

    /**
     * @see io.apicurio.hub.api.connectors.ISourceConnector#updateResourceContent(String, String, String, ResourceContent)
     */
    @Override
    public String updateResourceContent(String repositoryUrl, String commitMessage, String commitComment,
                                        ResourceContent content) throws SourceConnectorException {
        commitToBitbucket(repositoryUrl, content.getContent(), commitMessage, false);
        return null;
    }

    /**
     * @see io.apicurio.hub.api.connectors.ISourceConnector#createResourceContent(String, String, String)
     */
    @Override
    public void createResourceContent(String repositoryUrl, String commitMessage, String content) throws SourceConnectorException {
        try {
            this.validateResourceExists(repositoryUrl);
            throw new SourceConnectorException("Cannot create resource (already exists): " + repositoryUrl);
        } catch (NotFoundException | ApiValidationException e) {
            // This is what we want!
        }
        commitToBitbucket(repositoryUrl, content, commitMessage, true);
    }

    /**
     * @see IBitbucketSourceConnector#getTeams()
     */
    @Override
    public Collection<BitbucketTeam> getTeams() throws BitbucketException, SourceConnectorException {
        logger.debug("Getting the Bitbucket teams for current user");

        try {
            String teamsUrl = endpoint("/teams?role=member").url();

            HttpRequest request = Unirest.get(teamsUrl);
            addSecurityTo(request);
            HttpResponse<com.mashape.unirest.http.JsonNode> response = request.asJson();

            JSONObject responseObj = response.getBody().getObject();

            if (response.getStatus() != 200) {
                throw new UnirestException("Unexpected response from Bitbucket: " + response.getStatus() + "::" + response.getStatusText());
            }

            Collection<BitbucketTeam> rVal =  new HashSet<>();

            // TODO response is paged - make sure we consume and return all data!
            responseObj.getJSONArray("values").forEach(obj -> {
                BitbucketTeam bbt = new BitbucketTeam();
                JSONObject team = (JSONObject) obj;
                bbt.setDisplayName(team.getString("display_name"));
                bbt.setUsername(team.getString("username"));
                bbt.setUuid(team.getString("uuid"));
                rVal.add(bbt);
            });

            return rVal;

        } catch (UnirestException e) {
            throw new BitbucketException("Error getting Bitbucket teams.", e);
        }
    }

    @Override
    public Collection<BitbucketRepository> getRepositories(String teamName) throws BitbucketException, SourceConnectorException {
        try {
        	//@formatter:off
        	Endpoint endpoint = endpoint("/repositories/:uname")
        			.bind("uname", teamName)
        			.queryParam("pagelen", "100");
        	if (!"".equals(config.getRepositoryFilter())) {
        		endpoint = endpoint.queryParam("q", "name%7E%22:filter%22").bind("filter", config.getRepositoryFilter());
        	}
        	
            String teamsUrl = endpoint.url();
            //@formatter:on;

            HttpRequest request = Unirest.get(teamsUrl);
            addSecurityTo(request);
            HttpResponse<com.mashape.unirest.http.JsonNode> response = request.asJson();

            JSONObject responseObj = response.getBody().getObject();

            if (response.getStatus() != 200) {
                throw new UnirestException("Unexpected response from Bitbucket: " + response.getStatus() + "::" + response.getStatusText());
            }

            Collection<BitbucketRepository> rVal =  new HashSet<>();

            // TODO response is paged - make sure we consume and return all data!
            responseObj.getJSONArray("values").forEach(obj -> {
                BitbucketRepository bbr = new BitbucketRepository();
                JSONObject rep = (JSONObject) obj;
                bbr.setName(rep.getString("name"));
                bbr.setUuid(rep.getString("uuid"));
                bbr.setSlug(rep.getString("slug"));
                rVal.add(bbr);
            });

            return rVal;

        } catch (UnirestException e) {
            throw new BitbucketException("Error getting Bitbucket teams.", e);
        }
    }
    
    /**
     * @see io.apicurio.hub.api.bitbucket.IBitbucketSourceConnector#getBranches(java.lang.String, java.lang.String)
     */
    @Override
    public Collection<SourceCodeBranch> getBranches(String group, String repo)
            throws BitbucketException, SourceConnectorException {
        try {
            //@formatter:off
            String branchesUrl = endpoint("/repositories/:uname/:repo/refs/branches")
                    .bind("uname", group)
                    .bind("repo", repo)
                    .toString();
            //@formatter:on;

            HttpRequest request = Unirest.get(branchesUrl);
            addSecurityTo(request);
            HttpResponse<com.mashape.unirest.http.JsonNode> response = request.asJson();

            JSONObject responseObj = response.getBody().getObject();

            if (response.getStatus() != 200) {
                throw new UnirestException("Unexpected response from Bitbucket: " + response.getStatus() + "::" + response.getStatusText());
            }

            Collection<SourceCodeBranch> rVal =  new HashSet<>();

            // TODO response is paged - make sure we consume and return all data!
            responseObj.getJSONArray("values").forEach(obj -> {
                JSONObject b = (JSONObject) obj;
                SourceCodeBranch branch = new SourceCodeBranch();
                branch.setName(b.getString("name"));
                branch.setCommitId(b.getJSONObject("target").getString("hash"));
                rVal.add(branch);
            });

            return rVal;

        } catch (UnirestException e) {
            throw new BitbucketException("Error getting Bitbucket teams.", e);
        }
    }

    /**
     * Adds security information to the http request.
     * @param request
     */
    @Override
    protected void addSecurityTo(HttpRequest request) throws SourceConnectorException {
        if (this.getExternalTokenType() == TOKEN_TYPE_BASIC) {
            request.header("Authorization", "Basic " + getExternalToken());
        }
        if (this.getExternalTokenType() == TOKEN_TYPE_OAUTH) {
            request.header("Authorization", "Bearer " + getExternalToken());
        }
    }

    /**
     * Commits new repository file content to Bitbucket.
     * @param repositoryUrl
     * @param content
     * @param commitMessage
     * @param create
     * @throws SourceConnectorException
     */
    private String commitToBitbucket(String repositoryUrl, String content, String commitMessage, boolean create) throws SourceConnectorException {

        BitbucketResource resource = BitbucketResourceResolver.resolve(repositoryUrl);

        try {
            //@formatter:off
            String contentUrl = endpoint("/repositories/:team/:repo/src")
                    .bind("team", resource.getTeam())
                    .bind("repo", resource.getRepository())
                    .url();
            //@formatter:on

            InputStream filesStream = null;
            try {
                filesStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException e) {
                throw new SourceConnectorException("Error writing content to file stream.");
            }

            HttpRequestWithBody request = Unirest.post(contentUrl);
            addSecurityTo(request);

            //@formatter:off
            HttpResponse<com.mashape.unirest.http.JsonNode> response = request
                    .field(resource.getResourcePath(), filesStream, resource.getResourcePath())
                    .field("message", commitMessage)
                    /*.field("branch", resource.getSlug())*/ // for now, just put the content on master
                    .asJson();
            //@formatter:on

            int responseStatus = response.getStatus();
            if (responseStatus != 201) {
                throw new UnirestException("Unexpected response from Bitbucket: " + responseStatus + "::" + response.getStatusText());
            }
        } catch (UnirestException e) {
            throw new SourceConnectorException(e);
        }

        return null;
    }

    private ResourceContent getResourceContentFromBitbucket(BitbucketResource resource) throws NotFoundException, SourceConnectorException {
        try {
            //@formatter:off
            String contentUrl = endpoint("/repositories/:team/:repo/src/:branch/:path")
                    .bind("team", resource.getTeam())
                    .bind("repo", resource.getRepository())
                    .bind("branch", resource.getSlug())
                    .bind("path", resource.getResourcePath())
                    .url();
            //@formatter:on

            HttpRequest request = Unirest.get(contentUrl);
            try {
                addSecurityTo(request);
            } catch (Exception e) {
                // If adding security fails, just go ahead and try without security.  If it's a public
                // repository then this will work.  If not, then it will fail with a 404.
            }
            HttpResponse<InputStream> response = request.asBinary();

            ResourceContent rVal = new ResourceContent();
            
            if (response.getStatus() == 404) {
                throw new NotFoundException();
            }

            if (response.getStatus() != 200) {
                throw new UnirestException("Unexpected response from Bitbucket: " + response.getStatus() + "::" + response.getStatusText());
            }

            String content = null;
            try (InputStream cstream = response.getBody()) {
                content = IOUtils.toString(cstream, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new SourceConnectorException("Error parsing file stream from Bitbucket");
            }

            rVal.setSha(null); // sha is no longer used
            rVal.setContent(content);

            return rVal;

        } catch (UnirestException e) {
            throw new SourceConnectorException(e);
        }
    }
    
    /**
     * @see io.apicurio.hub.api.connectors.ISourceConnector#createPullRequestFromZipContent(java.lang.String, java.lang.String, java.util.zip.ZipInputStream)
     */
    @Override
    public String createPullRequestFromZipContent(String repositoryUrl, String commitMessage,
            ZipInputStream generatedContent) throws SourceConnectorException {
        return null;
    }
}
