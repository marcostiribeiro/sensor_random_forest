
import pickle5 as pickle
from builtins import print
import psutil

import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.svm import LinearSVC
from sklearn.naive_bayes import MultinomialNB
from sklearn.naive_bayes import GaussianNB
from sklearn.neural_network import MLPClassifier

from sklearn.metrics import accuracy_score
from datetime import datetime

from os import path
import os
import sys

import getpass
import subprocess
import socket

# import requests
# from getmac import get_mac_address as gma
# # from Crypto.Cipher import AES
# import random, string, base64


from Cryptodome.Cipher import AES
import random, string, base64
from Cryptodome.Random import get_random_bytes
from base64 import b64encode
from base64 import b64decode




import gc


## rest API request
import requests


## Mac Address
from getmac import get_mac_address as gma

##Pass arguments for applications
import argparse


headers = {
    'Content-Type': 'application/json',
}




class sensor:

    def __init__(self) -> None:


        self.ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
        self.loop = True
        options = self.args()
        self.model = self.create_model()
        self.interfaceLan = options.interface
        self.interface_controller = options.interface_controller
        self.server_ip = options.sdn

        self.server_web = options.webserver

    #
    # Database insert parameters
    #
    def send_database_parameter(self, parameter):
        print("send_database_parameter método")
        print("send_database_parameter método")
        print(parameter)

    #
    #
    #
    def send_controller_parameter(self,parameter):
        print("send_controller_parameter")
        print("send_controller_parameter")
        print(parameter)


    def main(self):
        while self.loop:
            try:
                self.capture_packet_network()
                self.teste_network_data()
                os.remove(self.ROOT_DIR + "/files/pcap_info/out.pcap")
            except ValueError as exception:
                if exception.args[0] == "short flow":
                    print("Insufficient flow")


    #
    #  Capture pcap packet
    #
    def capture_packet_network(self):

        pcap_file = open(self.ROOT_DIR + "/files/pcap_info/out.pcap", "w", encoding="ISO-8859-1")
        pcap_list = self.proc_capture_pcap(self.interfaceLan,1000)
        pcap_file.writelines(pcap_list)
        pcap_file.close()
        self.proc_execute_cicflowmeter()

    #
    # Process for packet capture
    #

    def proc_capture_pcap(self, interface: str, line_count: int = 5000) -> list:

        pcap_cmd = ["tcpdump", "-i", interface, "-s", "65535", "-w", "-"]
        process = subprocess.Popen(
            pcap_cmd,
            stdout=subprocess.PIPE,
            universal_newlines=False,
            encoding="ISO-8859-1",
        )

        counter = 0
        output_list = []

        while counter < line_count:
            line = process.stdout.readline()
            output_list.append(line)
            counter += 1

        process.stdout.close()
        exit_status = process.wait()

        if exit_status:
            raise subprocess.CalledProcessError(exit_status, pcap_cmd)

        return output_list

        #
        # Running cicflowmeter
        #
    def proc_execute_cicflowmeter(self):

        cic_cmd = ["sh", "cfm", self.ROOT_DIR + "/files/pcap_info", self.ROOT_DIR + "/files/flow_output"]
        process = subprocess.Popen(
            cic_cmd,
            cwd=self.ROOT_DIR + "/files/external/CICFlowMeter-4.0/bin",
            stdout=subprocess.DEVNULL,
        )

        exit_status = process.wait()
        if exit_status:
            raise subprocess.CalledProcessError(exit_status, cic_cmd)




    ################### block code args ##################################3
    def args(self):
        parse =  self.parseOptions()
        args = parse.parse_args()
        return args

    ##create  options for parameter applications
    def parseOptions(self):

        # set parameter
        arg = argparse.ArgumentParser(
            description="Applications to detect attack DDOS in SDN system and other")
        # Set IP device for mitigation attack, SDN ou Other.
        arg.add_argument(
            "--sdn",
            action="store",
            required=False,
            dest="sdn",
            help="Set IP for destination device or  IP SDN Controller",
            default=None,
        )


        # Set LAN interface for collect flow
        arg.add_argument(
            "--interface",
            action="store",
            required=True,
            dest="interface",
            help="Select Lan interface for collect flow",
            default="enp0s8",
        )

        # Set LAN interface for server controller
        arg.add_argument(
            "--interface_controller",
            action="store",
            required=True,
            dest="interface_controller",
            help="Select Lan interface for connect server controller",
            default="enp0s3",
        )


        # Set ip server web for monitoring
        arg.add_argument(
            "--webserver",
            action="store",
            required=False,
            nargs='+',
            dest="webserver",
            help="Set ip server web for monitoring  ",
            default=None,
        )
        return arg
    ################### block code args ##################################3

    ##################### block code Machine Learning ##################################################
    #
    # Transform pcap file
    #

    def tranform_flow_data(self):


        uri = self.ROOT_DIR + "/files/flow_output/out.pcap_Flow.csv"
        dataframe = self.load_dataset(uri)
        metadata = pd.DataFrame()
        metadata["from_ip"] = dataframe["Src IP"]
        metadata["to_ip"] = dataframe["Dst IP"]
        metadata["protocol"] = dataframe["Protocol"]
        metadata["from_port"] = dataframe["Src Port"]
        metadata["to_port"] = dataframe["Dst Port"]

        self.preprocessamento(dataframe)
        x_train, x_test, _, _ = self.train_test(dataframe)
        data = np.concatenate((x_test, x_train))

        return {"data": data, "metadata": metadata}



    #
    # Teste flow
    #

    def teste_network_data(self):
        try:
            # Parse the flow data
            flow_data = self.tranform_flow_data()
        except ValueError:
            raise ValueError("short flow")

        flow_features = flow_data["data"]
        metadata = flow_data["metadata"]

        predictions_rf  = self.model[1].predict(flow_features)
        predictions_rf__proba  = self.model[1].predict_proba(flow_features)


        for row, prediction, proba in zip(metadata.values, predictions_rf,predictions_rf__proba):
            # if prediction:
            from_ip, to_ip, proto, from_port, to_port = row
            # print(from_ip, to_ip, proto, from_port, to_port)
            result = [proba, from_ip, to_ip, proto, from_port, to_port]

            if to_ip in self.server_web:
                self.send_database_parameter(result)
                self.send_controller_parameter(result)


        #
        # Verifica se ja existe modelo treinado,
        # caso não, cria o modelo e serializa
        #
    def create_model(self):
            if path.exists(self.ROOT_DIR + "/model/" + "RF_model.pck"):
                model_file = open(self.ROOT_DIR + "/model/RF_model.pck", "rb")
                model = pickle.load(model_file)
                print("********* Selected Model    **********")
                print(model[0])
                print("**************************************")
                # models.append(model)

                model_file.close()
                return model
            else:
                print("Model is not trained")


    #
    # Split data traine and test
    #
    def train_test(self, dataframe):

        x_data = []
        y_data = []

        for row in dataframe.values:
            x_data.append(row[:-1])
            y_data.append(row[-1])



        x_train, x_test, y_train, y_test = train_test_split(x_data, y_data, random_state=1, test_size=0.10)

        return np.array(x_train), np.array(x_test), np.array(y_train), np.array(y_test)

    def load_dataset(self, uri):

        if (uri != self.ROOT_DIR + "/dataset/dataset_ddos.csv"):
            input_df = pd.read_csv(self.ROOT_DIR + "/files/flow_output/out.pcap_Flow.csv")
            return input_df
        chunksize = 1000

        initial_value = 1294529
        var_nrows = 460000  # 450000#440000#430000#420000

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True):
            list_of_dataframes.append(df)
        ddos_dados_1 = pd.concat(list_of_dataframes)
        features = ddos_dados_1.columns

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value):
            list_of_dataframes.append(df)
        ddos_dados_2 = pd.concat(list_of_dataframes)
        ddos_dados_2.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value * 2):
            list_of_dataframes.append(df)
        ddos_dados_3 = pd.concat(list_of_dataframes)
        ddos_dados_3.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value * 3):
            list_of_dataframes.append(df)
        ddos_dados_4 = pd.concat(list_of_dataframes)
        ddos_dados_4.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value * 4):
            list_of_dataframes.append(df)
        ddos_dados_5 = pd.concat(list_of_dataframes)
        ddos_dados_5.columns = features

        ddos_dados = pd.concat([ddos_dados_1, ddos_dados_2, ddos_dados_3, ddos_dados_4, ddos_dados_5])

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=6472647):
            list_of_dataframes.append(df)
        benign_dados_1 = pd.concat(list_of_dataframes)
        benign_dados_1.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value + 6472647):
            list_of_dataframes.append(df)
        benign_dados_2 = pd.concat(list_of_dataframes)
        benign_dados_2.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value * 2 + 6472647):
            list_of_dataframes.append(df)
        benign_dados_3 = pd.concat(list_of_dataframes)
        benign_dados_3.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value * 3 + 6472647):
            list_of_dataframes.append(df)
        benign_dados_4 = pd.concat(list_of_dataframes)
        benign_dados_4.columns = features

        list_of_dataframes = []
        for df in pd.read_csv(uri, chunksize=chunksize, nrows=var_nrows, index_col=0, low_memory=True,
                              skiprows=initial_value * 4 + 6472647):
            list_of_dataframes.append(df)
        benign_dados_5 = pd.concat(list_of_dataframes)
        benign_dados_5.columns = features

        benign_dados = pd.concat([benign_dados_1, benign_dados_2, benign_dados_3, benign_dados_4, benign_dados_5])

        dataframe = pd.concat([ddos_dados, benign_dados])

        return dataframe
    #
    # realizado o preprocessamento do dataframe
    # excluir os campo não utilizados
    #
    def preprocessamento(self, dataframe):
        dataframe.drop(["Flow ID", "Timestamp", "Src IP", "Dst IP", "Flow Byts/s", "Flow Pkts/s"],
                       inplace=True, axis=1, )
        dataframe["Label"] = dataframe["Label"].apply(lambda x: 1 if x == "ddos" else 0)

        for col in dataframe.columns:
            dataframe[col] = np.nan_to_num(dataframe[col])
        return dataframe

    ##################### block code Machine Learning ##################################################
def init():
    sensors = sensor()
    sensors.main()
if __name__ == "__main__":
    init()


