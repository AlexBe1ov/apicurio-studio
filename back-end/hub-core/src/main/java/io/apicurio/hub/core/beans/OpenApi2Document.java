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

package io.apicurio.hub.core.beans;

/**
 * @author eric.wittmann@gmail.com
 */
public class OpenApi2Document extends OpenApiDocument {
    
    private String swagger = "2.0";
    private String[] consumes = new String[] { "application/json" };
    private String[] produces = new String[] { "application/json" };
    
    /**
     * Constructor.
     */
    public OpenApi2Document() {
        
    }

    /**
     * @return the swagger
     */
    public String getSwagger() {
        return swagger;
    }

    /**
     * @param swagger the swagger to set
     */
    public void setSwagger(String swagger) {
        this.swagger = swagger;
    }

    /**
     * @return the consumes
     */
    public String[] getConsumes() {
        return consumes;
    }

    /**
     * @param consumes the consumes to set
     */
    public void setConsumes(String[] consumes) {
        this.consumes = consumes;
    }

    /**
     * @return the produces
     */
    public String[] getProduces() {
        return produces;
    }

    /**
     * @param produces the produces to set
     */
    public void setProduces(String[] produces) {
        this.produces = produces;
    }

}
