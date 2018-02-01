package com.ptcmanaged.tempthing;

import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
Copyright (c) 2017 PTC Inc.
Permission is hereby granted, free of charge, to any person obtaining a copy of this software
and associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
is furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies
or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
@ThingworxPropertyDefinitions(

        properties = {
                @ThingworxPropertyDefinition(
                        name = "Prop_Humidity",
                        description = "A Current humidity value from a AM2302 sensor.",
                        baseType = "NUMBER",
                        aspects = {
                                "dataChangeType:ALWAYS",
                                "dataChangeThreshold:0",
                                "cacheTime:0",
                                "isPersistent:FALSE",
                                "isReadOnly:TRUE",
                                "pushType:VALUE",
                                "defaultValue:0"
                        }
                ),
                @ThingworxPropertyDefinition(
                        name = "Prop_Temperature",
                        description = "A Current temperature and humidity value from a DHT22 sensor.",
                        baseType = "NUMBER",
                        aspects = {
                                "dataChangeType:ALWAYS",
                                "dataChangeThreshold:0",
                                "cacheTime:0",
                                "isPersistent:FALSE",
                                "isReadOnly:TRUE",
                                "pushType:VALUE",
                                "defaultValue:0"
                        }
                ),
        }
)

/**
 * This remote (Virtual) thing is responsible for obtaining values for the properties described in the annotations
 * above. This particular class interfaces with the AM2302 Humidity and Temperature Sensor
 * @link http://www.adafruit.com/products/393 (alternatively use AOSONG 2302 AKA DHT22 @ link https://www.adafruit.com/products/385).
 * It is intended to poll the sensor by periodically running the python script provided in this Git Hub repository
 * @link https://github.com/PTC-Academic/Adafruit_Python_DHT.git using the instructions found here
 * @link https://learn.adafruit.com/dht-humidity-sensing-on-raspberry-pi-with-gdocs-logging/software-install-updated .
 * It parses the output of the python script and then pushes the temperature and humidity values up to the ThingWorx
 * server whenever processScanRequest() is called.
 *
 * There are many different ways to collect data as well as deliver it to ThingWorx. This is indended as a simple,
 * flexible example than should work in almost any environment.
 *
 * This example can operate without the presence of the AM2302 hardware if the simulated parameter is provided when
 * the class is constructed.
 */
public class TempAndHumidityThing extends VirtualThing {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String PI_HOME = "/home/pi";
    public static final String PATH_TO_TEMP_HUMID_COMMAND = "/projects/Adafruit_Python_DHT/examples/AdafruitDHT.py";

    private final String name;
    private final String description;
    private final String simulated;
    private final ConnectedThingClient client;

    public TempAndHumidityThing(String name, String description, ConnectedThingClient client, String simulated) {
        super(name, description, client);
        this.name = name;
        this.description = description;
        this.client = client;
        this.simulated=simulated;

        try {
            initializeFromAnnotations();
            setDefaultPropertyValue("Prop_Temperature");
            setDefaultPropertyValue("Prop_Humidity");
        } catch (Exception localException) {
            LOG.error("Failed to set default value.", localException);
        }
    }

    /**
     * Read the current value of this temperature sensor.
     *
     * @throws Exception
     */
    @Override
    public void processScanRequest() throws Exception {
        super.processScanRequest();

        Double currentTemperatureF = getTemperature();
        Double currentHumidity = getHumidity();
        LOG.debug("Prop_Temperature" + "=" + currentTemperatureF);
        LOG.debug("Prop_Humidity" + "=" + currentHumidity);
        setProperty("Prop_Temperature", currentTemperatureF);
        setProperty("Prop_Humidity", currentHumidity);
        updateSubscribedProperties(2000);

    }

    private Double getTemperature() {
        String consoleOutput;
        if (simulated!=null&&simulated.equals("simulated")) {
            consoleOutput = getSimulatedConsoleOutput();
        } else {
            consoleOutput = getCommandResults("sudo " + PI_HOME + PATH_TO_TEMP_HUMID_COMMAND + " 2302 4");
        }
        Double temperature = parseTemperatureFromString(consoleOutput);

        return temperature;

    }

    private String getSimulatedConsoleOutput() {

        float randHumid = (10 + (int) (Math.random() * 1000))/10;
        float randTemp = (200 + (int) (Math.random() * 2000))/10;
        return String.format("Temp=%.1f*C Humidity=%.1f%%", randTemp,randHumid);
    }

    private Double getHumidity() {
        String consoleOutput;
        if (simulated!=null&&simulated.equals("simulated")) {
            consoleOutput = getSimulatedConsoleOutput();
        } else {
            consoleOutput = getCommandResults("sudo " + PI_HOME + PATH_TO_TEMP_HUMID_COMMAND + " 2302 4");
        }
        Double humidity = parseHumidityFromString(consoleOutput);

        return humidity;

    }

    Double parseTemperatureFromString(String consoleOutput) {
        String[] tempHumidParts = consoleOutput.split(" +");
        String[] tempPart = tempHumidParts[0].split("=");
        String theTemp="0";
        if(tempPart.length>0) {
            theTemp = tempPart[1];
            theTemp = theTemp.replace("*C", "");
        }
        return Double.parseDouble(theTemp);
    }

    Double parseHumidityFromString(String consoleOutput) {
        String[] tempHumidParts = consoleOutput.split(" +");
        String[] humidityPart = tempHumidParts[1].split("=");
        String theHumidity="0";
        if(humidityPart.length>0) {
            theHumidity = humidityPart[1];
            theHumidity = theHumidity.replace("%", "");
        }

        return Double.parseDouble(theHumidity);
    }

    /**
     * Sets the current value of a property to the default value provided in its annotation.
     * @param propertyName
     * @throws Exception
     */
    protected void setDefaultPropertyValue(String propertyName) throws Exception {
        setProperty(propertyName, getProperty(propertyName).getPropertyDefinition().getDefaultValue().getValue());
    }

    /**
     * Run a shell command and get the results back as a string.
     *
     * @param commandLine Note: Assume there is no existing PATH environment variable.
     * @return the console output of the command.
     */
    protected String getCommandResults(String commandLine) {
        String s = null;
        StringBuffer retBuff = new StringBuffer();
        try {

            // using the Runtime exec method:
            Process p = Runtime.getRuntime().exec(commandLine);

            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));

            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                System.out.println("stdout>" + s);
                retBuff.append(s);
            }

            // read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println("stderr>" + s);
                retBuff.append(s);
            }
        } catch (IOException e) {
            LOG.error("An exception occurred while running an external script. ", e);
        }
        return retBuff.toString();
    }

}
