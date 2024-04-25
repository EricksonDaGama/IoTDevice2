DEVICE_SRC := IoTDevice \
MessageCode

SERVER_SRC := Device \
Domain \
IoTServer \
ServerThread \
ServerManager \
ServerResponse \
DomainStorage \
DeviceStorage \
UserStorage \
Utils

BIN_DIR := bin
DEVICE_DIR := src.iotclient
SERVER_DIR := src.iotserver

DEVICE_FULL_PATHS := $(addsuffix .java,$(addprefix $(DEVICE_DIR)/,$(DEVICE_SRC))) 
SERVER_FULL_PATHS := $(addsuffix .java,$(addprefix $(SERVER_DIR)/,$(SERVER_SRC))) 

all:
	javac -d bin $(DEVICE_FULL_PATHS) $(SERVER_FULL_PATHS)
	jar cvfe IoTDevice.jar src.iotclient.IoTDevice -C ./bin $(DEVICE_DIR) \
-C ./bin src.iohelper/FileHelper.class
	jar cvfe IoTServer.jar src.iotserver.IoTServer -C ./bin $(SERVER_DIR) \
-C ./bin src.iotclient/MessageCode.class -C ./bin src.iohelper/FileHelper.class
	chmod +x ./attestation.sh
	./attestation.sh
clean:
	rm -r bin; mkdir bin
