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

import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

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


@Component(immediate = true,
           service = {SomeInterface.class},
           property = {
               "someProperty=Some Default String Value",
           })
public class AppComponent implements SomeInterface {

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
        // try {
        //     // Instancia o ServerSocket ouvindo a porta 12345
        //     servidor = new ServerSocket(12345);
        //     log.info("Servidor ouvindo a porta 12345");
        //     while(true) {
        //         log.info("Aguardando conexão...");
        //         // o método accept() bloqueia a execução até que o servidor receba um pedido de conexão
        //         Socket cliente = servidor.accept();
        //         log.info("Cliente conectado: " + cliente.getInetAddress().getHostAddress());
        //         ObjectInputStream entrada = new ObjectInputStream(cliente.getInputStream());
        //         String msg = (String)entrada.readObject();
        //         log.info("Mensagem recebida do cliente: "+msg);
        //         entrada.close();
        //         cliente.close();
        //     }
        // }
        // catch(Exception e) {
        //     System.out.println("Erro ao abrir o socket server: " + e.getMessage());
        // }
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        // try{
        //     log.info("Fechando socket server...");
        //     servidor.close();
        //     log.info("Socket Server fechado!");
        // }catch(Exception e){
        //     log.error("Erro ao fechar o socket server.", e);
        // }
        
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
                log.info(("##### Received:"+ inFromClient.readLine() + " #####"));
            } catch(IOException e){
                log.info("Erro de buffer");
            }
        }
        log.info("##### Saindo, Conexão e Portas Encerradas #####");
        activeEX = false;
        //return true;
    }
    //Fim do método interno

}
