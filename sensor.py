import mysql.connector
from mysql.connector import errorcode
import sys
import socket

#try:
db_connection = mysql.connector.connect(host='localhost', user='root', password='mysqlonos',database='teste')
#cursor = db_connection.cursor()
print("Conexão estabelecida com sucesso")
#except mysql.connector.Error as error:
#    if error.errno == errorcode.ER_BAD_DB_ERROR:
#        print("Base de dados inexistente");
#    elif error.errno == errorcode.ER.ACCESS_DENIED_ERROR:
#        print("Erro no login")
#    else:
#        print(error)
#else:
#    db_connection.close()

# Variável que manipula as coisas do BD
cursor = db_connection.cursor()
# Comandos do Socket Client
HOST = 'localhost'
PORTA = 12345

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.connect((HOST, PORTA))
    
    # Busca no BD
    sql = "SELECT * FROM trusted WHERE IPPorta LIKE \'10.0.0.1:9622\'"
    print(sql)
    cursor.execute(sql);

    for (id,IPPorta,score) in cursor:
        print(id,IPPorta,score)
        scoreRes = score

    # Envia dados para o cliente
    s.send(str(scoreRes).encode("utf-8"))

    print("Conexão com o cliente finalizada")

db_connection.close()