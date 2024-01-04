/*
 * Copyright 2023-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.foo.app;

import com.google.common.collect.Maps;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
// Adicionado em 09/11
import org.onlab.packet.ICMP;
import org.onlab.packet.IPacket;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.UDP;

import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.Application;
import org.onosproject.core.CoreService;
//import org.onosproject.app.AbstractApplication;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
//import org.onosproject.net.flowobjective.ForwardingObjectiveFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;
import java.util.Optional;

import static org.onlab.util.Tools.get;

// Comunicação via socket
import java.io.*;
import java.net.*;
import java.lang.System;

//Executores do Filipe
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
           //service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface  {
//public class AppComponent implements Application  {

    private final Logger log = LoggerFactory.getLogger(getClass());
    ServerSocket servidor;
    //Executores interno e regular
    private ExecutorService service = Executors.newSingleThreadExecutor();
    private ExecutorService serviceinner = Executors.newSingleThreadExecutor();
    //variaveis de verificação de serviço
    public boolean activeEX = false;
    public boolean work = true;
    public ServerSocket server = null;
    public Socket connected = null;

    /** Some configurable property. */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    // Instantiates the relevant services.
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    protected Map<DeviceId, Map<MacAddress, PortNumber>> macTables = Maps.newConcurrentMap();
    private ApplicationId appId;
    private PacketProcessor processor;
    
    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        //
        //Parte de inicialização da comunicação
        //
        Runnable runnable = () ->{
            catcher();
            //log.info("Logado");
        };
        work = true;
        log.info("##### Serviço de Comunicação Iniciado #####");
        service.execute(runnable);
        //
        //Fim da inicialização da comunicação
        //
        log.info("Started");

        //flowRuleService = coreService.getFlowRuleService();
        //flowRuleService = context.getService(FlowRuleService.class);

        // Parte da inicialização do fwd
        appId = coreService.getAppId("org.foo.app"); //equal to the name shown in pom.xml file
    
        processor = new SwitchPacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(3));

        /*
         * Restricts packet types to IPV4 and ARP by only requesting those types.
         */
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4).build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP).build(), PacketPriority.REACTIVE, appId, Optional.empty());
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        //
        //Encerrando serviço de comunicação
        //
        work = false;
        try {
            server.close();
            connected.close();
        }catch (Exception e){
            log.info("Erro ao fechar socket server e conexão");
        }
        log.info("### Encerrando Comunicação ###");
        if(activeEX){
            serviceinner.shutdown();
            serviceinner = null;
            log.info("### Encerrando Comunicação Interna ###");
        }
        log.info("### Encerrando Comunicação Externa###");
        service.shutdown();
        service = null;
        //
        //Encerrado
        //

        log.info("Stopped");
        packetService.removeProcessor(processor);
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    //Parte de inicialização e repetição de comunicação pra adição de informação
    public void catcher(){

        while(work){
        Runnable runnable2 = () ->{
            innerCom();
            //log.info("Logado");
        };
        log.info("##### Nova escuta Iniciada #####");
        serviceinner.execute(runnable2);
        activeEX = true;
        while((activeEX && work)){
                //espera
        }
        serviceinner.shutdownNow();
        }
    }

    //Metodo interno
    public void innerCom(){

        log.info("Starting Server");

        boolean ent = true;
        //PacketContext pc;
        PortNumber outPort = null;
        MacAddress srcMac = null;
        String trustScore = null;

        try{
            server = new ServerSocket(12345);
        }catch(IOException ie){
            log.info("Erro de Socket");
            ent = false;
        }

        //Start loop (atm is infinity need to think how to change it in the future)
        while(work){
            try{
                connected  = server.accept();
            }catch(IOException ie){
                log.info("Servidor não Connectado");
            }

            log.info(("The Client "+ connected.getInetAddress() + ":" + connected.getPort() + " is connected"));
            BufferedReader inFromClient = null;
            try{
                inFromClient = new BufferedReader(new InputStreamReader(connected.getInputStream()));
                trustScore = inFromClient.readLine();
                log.info(("##### Received:"+ trustScore + " #####"));
            } catch(IOException e){
                log.info("Erro de buffer");
            }
            // 20/12/2023: Tentativa de redirecionamento de tráfego sobrepondo a regra de fluxo instalada anteriormente com uma prioridade mais alta
            if (trustScore != null){
                outPort = outPort.portNumber("3");
                srcMac = srcMac.valueOf("00:00:00:00:00:01");
                DeviceId device = DeviceId.deviceId("of:0000000000000001");
                
                // Crie uma instância da regra de fluxo
                /*FlowRule flowRule = new DefaultFlowRule();

                // Defina os parâmetros da regra de fluxo
                flowRule.setSrcPort(1);
                flowRule.setDstPort(3);
                flowRule.setPriority(65535);
                flowRule.setAction(FlowRule.Action.FORWARD);

                // Defina o switch no qual a regra será instalada
                //flowRule.setSwitch(device);

                // Aplique a regra de fluxo
                flowRule.apply();*/

                TrafficSelector selector = DefaultTrafficSelector.builder()
                    // Defina seus critérios aqui (por exemplo, matchEthSrc, matchEthDst, matchInPort, etc.)
                    .matchEthSrc(srcMac) // Exemplo: correspondência na porta de entrada 1
                    .build();
                
                // Ação a ser tomada pela regra
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort) // Encaminhar para a porta de saída desejada
                .build();
                
                
                //pc.treatmentBuilder().setOutput(outPort);*/

                FlowRule fr = DefaultFlowRule.builder()
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .forDevice(device).withPriority(65535)
                        .makeTemporary(60)
                        .fromApp(appId).build();

                //flowRuleService.applyFlowRules(fr);
                //pc.send();*/
                /*ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(65535)
                    .fromApp(appId) // appId é o ID da sua aplicação
                    .withFlag(ForwardingObjectiveFlag.NOTIFY) // Especifique a flag conforme necessário
                    .add();*/

                // Instalação da regra no dispositivo específico
                //flowRuleService.forward(device, forwardingObjective);
                flowRuleService.applyFlowRules(fr);
            }
        }
        log.info("##### Saindo, Conexão e Portas Encerradas #####");
        activeEX = false;
        //return true;
    }
    //Fim do método interno

    //Execução do redirecionamento de pacotes
    /**
     * This class contains pseudo code that you must replace with your own code.  Your job is to
     * send the packet out the port previously learned for the destination MAC, if it
     * exists. Otherwise flood the packet (to all ports).
     */
    private class SwitchPacketProcessor implements PacketProcessor {
        /**
         * Learns the source port associated with the packet's DeviceId if it has not already been learned.
         * Calls actLikeSwitch to process and send the packet.
         * @param pc PacketContext object containing packet info
         */
        @Override
        public void process(PacketContext pc) {
            log.info(pc.toString());
            initMacTable(pc.inPacket().receivedFrom());


            // This is the basic flood all ports switch that is enabled.
            //actLikeHub(pc);

            /*
             * This is the call to the actLikeSwitch method you will be creating. When
             * you are ready to test it, uncomment the line below, and comment out the
             * actLikeHub call above.
             */
            actLikeSwitch(pc);

        }

        /**
         * Example method. Floods packet out of all switch ports.
         *
         * @param pc the PacketContext object passed through from activate() method
         */
        public void actLikeHub(PacketContext pc) {
            // Mexi nessa função para não dar FLOOD no caso do ARP, mas sim redirecionar só para a porta 2.
            // Estou fazendo isso porque configurei todos os servidores com o mesmo MAC e IP, então obtendo a resposta
            // eu consigo somente redirecionar a porta.
            PortNumber outPort = null;
            //outPort = outPort.portNumber("2");
            //pc.treatmentBuilder().setOutput(PortNumber.FLOOD);
            pc.treatmentBuilder().setOutput(outPort.portNumber("2"));
            pc.send();
        }

        /**
         * Ensures packet is of required type. Obtain the PortNumber associated with the inPackets DeviceId.
         * If this port has previously been learned (in initMacTable method) build a flow using the packet's
         * out port, treatment, destination, and other properties.  Send the flow to the learned out port.
         * Otherwise, flood packet to all ports if out port is not learned.
         *
         * @param pc the PacketContext object passed through from activate() method
         */
        public void actLikeSwitch(PacketContext pc) {

            /*
             * Ensures the type of packet being processed is only of type IPV4 (not LLDP or BDDP).  If it is not, return
             * and do nothing with the packet. actLikeSwitch can only process IPV4 packets.
             */
            Short type = pc.inPacket().parsed().getEtherType();
            if (type != Ethernet.TYPE_IPV4 && type != Ethernet.TYPE_ARP) {
                return;
            }

            /*
             * Learn the destination, source, and output port of the packet using a ConnectPoint and the
             * associated macTable.  If there is a known port associated with the packet's destination MAC Address,
             * the output port will not be null.
             */
            ConnectPoint cp = pc.inPacket().receivedFrom();
            Map<MacAddress, PortNumber> macTable = macTables.get(cp.deviceId());
            MacAddress srcMac = pc.inPacket().parsed().getSourceMAC();
            MacAddress dstMac = pc.inPacket().parsed().getDestinationMAC();
            macTable.put(srcMac, cp.port());
            PortNumber outPort = macTable.get(dstMac);

            /*
             * If port is known, set pc's out port to the packet's learned output port and construct a
             * FlowRule using a source, destination, treatment and other properties. Send the FlowRule
             * to the designated output port.
             */
            if (outPort != null) {
                log.info("Entrei aqui. a porta é: " + outPort.toString());
                pc.treatmentBuilder().setOutput(outPort);
                FlowRule fr = DefaultFlowRule.builder()
                        .withSelector(DefaultTrafficSelector.builder().matchEthDst(dstMac).build())
                        .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                        .forDevice(cp.deviceId()).withPriority(PacketPriority.REACTIVE.priorityValue())
                        .makeTemporary(60)
                        .fromApp(appId).build();

                flowRuleService.applyFlowRules(fr);
                pc.send();
            } else {
            /*
             * else, the output port has not been learned yet.  Flood the packet to all ports using
             * the actLikeHub method
             */
                actLikeHub(pc);
            }
        }

        /**
         * puts the ConnectPoint's device Id into the map macTables if it has not previously been added.
         * @param cp ConnectPoint containing the required DeviceId for the map
         */
        private void initMacTable(ConnectPoint cp) {
            macTables.putIfAbsent(cp.deviceId(), Maps.newConcurrentMap());

        }
    }
}
