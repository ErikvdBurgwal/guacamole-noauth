
package net.deltalima.guacamole;

/*
 *  Guacamole - Clientless Remote Desktop
 *  Copyright (C) 2012  Laurent Meunier
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import net.sourceforge.guacamole.GuacamoleException;
import net.sourceforge.guacamole.net.auth.AuthenticationProvider;
import net.sourceforge.guacamole.net.auth.Credentials;
import net.sourceforge.guacamole.properties.FileGuacamoleProperty;
import net.sourceforge.guacamole.properties.GuacamoleProperties;
import net.sourceforge.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;


public class NoAuthenticationProvider implements AuthenticationProvider {

    private Logger logger = LoggerFactory.getLogger(NoAuthenticationProvider.class);
    private Map<String, GuacamoleConfiguration> configs;
    private long configTime;

    /**
     * The filename of the XML file to read the user mapping from.
     */
    public static final FileGuacamoleProperty  NOAUTH_CONFIG = new FileGuacamoleProperty() {
        @Override
        public String getName() {
            return "noauth-config";
        }
    };

    private File getConfigurationFile() throws GuacamoleException {
        // Get configuration file
        return GuacamoleProperties.getProperty(NOAUTH_CONFIG);
    }

    public synchronized void init() throws GuacamoleException {
        // Get configuration file
        File configFile = getConfigurationFile();
        if(configFile == null) {
            throw new GuacamoleException("Missing \"noauth-config\" parameter required for NoAuthenticationProvider.");
        }

        logger.info("Reading configuration file: {}", configFile);
        
        // Parse document
        try {
            // Set up parser
            NoAuthConfigContentHandler contentHandler = new NoAuthConfigContentHandler();

            XMLReader parser = XMLReaderFactory.createXMLReader();
            parser.setContentHandler(contentHandler);

            // Read and parse file
            Reader reader = new BufferedReader(new FileReader(configFile));
            parser.parse(new InputSource(reader));
            reader.close();

            // Init configs
            configTime = configFile.lastModified();
            configs = contentHandler.getConfigs();
        }
        catch (IOException e) {
            throw new GuacamoleException("Error reading configuration file: " + e.getMessage(), e);
        }
        catch (SAXException e) {
            throw new GuacamoleException("Error parsing XML file: " + e.getMessage(), e);
        }

    }
    @Override
    public Map<String, GuacamoleConfiguration> getAuthorizedConfigurations(Credentials credentials) throws GuacamoleException {
        // Check mapping file mod time
        File configFile = getConfigurationFile();
        if (configFile.exists() && configTime < configFile.lastModified()) {
            // If modified recently, gain exclusive access and recheck
            synchronized (this) {
                if (configFile.exists() && configTime < configFile.lastModified()) {
                    logger.info("Config file {} has been modified.", configFile);
                    init(); // If still not up to date, re-init
                }
            }
        }

        // If no mapping available, report as such
        if (configs == null) {
            throw new GuacamoleException("Configuration could not be read.");
        }

        return configs;
    }

    private static class NoAuthConfigContentHandler extends DefaultHandler {
        private Map<String, GuacamoleConfiguration> configs = new HashMap<String, GuacamoleConfiguration>();
        private String current = null;
        private GuacamoleConfiguration currentConfig = null;

        public Map<String, GuacamoleConfiguration> getConfigs() {
            return Collections.unmodifiableMap(configs);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if(localName.equals("config")) {
                configs.put(current, currentConfig);
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if(localName.equals("config")) {
                current = attributes.getValue("name");
                currentConfig = new GuacamoleConfiguration();
                currentConfig.setProtocol(attributes.getValue("protocol"));
                return;
            } else if(localName.equals("param")) {
                currentConfig.setParameter(attributes.getValue("name"),
                                           attributes.getValue("value"));
            }
        }
    }
}
