# Librería Java BorderSens SDK

## Contenido

En el presente repositorio, podemos encontrar el siguiente contenido:

- [BorderSensSDK](./BorderSensSDK): Proyecto java que contiene el código de la librería desarrollada para el contexto de uso en el proyecto BorderSens.
- [BorderSensSDK-1.0.0-jar-with-dependencies.jar](./BorderSensSDK-1.0.0-jar-with-dependencies.jar): Librería [BorderSensSDK](./BorderSensSDK) compilada en forma de jar, para poder ser usada en otros proyectos java.
- [BorderSensSDKTest](./BorderSensSDKTest): Proyecto de test que usa [BorderSensSDK-1.0.0-jar-with-dependencies.jar](./BorderSensSDK-1.0.0-jar-with-dependencies.jar) para interactuar con la librería y de esa forma servir como ejemplo de uso de la misma.

## Servicios de Azure

Los servicios Azure siguen el esquema propuesto en la arquitectura

![](./img/current_architecture.drawio.png)

Todos los servicios descritos para la plataforma bordersens, están desplegados en Azure, dentro del grupo de recursos **BorderSensPlarform**.

Se ha creado el siguiente usuario, para poder acceder a los mismos:

- **usuario:** bordersens@azurebiizertis.onmicrosoft.com
- **password:** XK_'T}JB&RBy

Para acceder a los servicios creados para el proyecto BorderSens, tenemos que acceder al grupo de recursos Bordersens

![](./img/grupo_recursos.png)

Y dentro veremos todos los recursos de este proyecto, que en el momento de la redacción de este documento, son los siguientes (aunque evolucionarán al menos con la construcción de la capa de IA)

![](./img/services.jpg)

A continuación se describirán brevemente los servicios Azure y  su rol dentro de la plataforma para dar contexto

### Azure IoT Hub

**Descripción:** Es el servicio de Azure  que sirve de punto de entrada entre la comunicación bidireccional a los dispositivos, y la plataforma, usando los mecanismos de autentificación y cifrado establecidos, en este caso certificados X509. De forma adicional este servicio se conecta a el Azure Service Bus, comentado mas adelante, iniciando de esta forma el flujo del dato a través de todos los servicios involucrados.

**Servicio en Azure:**  bs-iothub-service

### Azure IoT Hub Device Provisioning Service (DPS)

**Descripción:** Es el servicio de Azure  para gestionar los dispositivos conectados a Azure IoT Hub de una forma unificada y por lo tanto es el punto de acceso para el registro de un nuevo dispositivo.

**Servicio en Azure:**  bs-iot-provisioning-service

### Azure Cosmos DB

**Descripción:** Base de datos No-SQL (en forma de clave valor, muy similar a Cassandra), con altísima capacidad tanto en lectura como en escritura, donde se guardaran todos los registros relativos a el transito del dato por la plataforma.

**Servicio en Azure:**  bs-iot-provisioning-service

### Azure Service Bus

**Descripción:** Es el servicio de Azure  que sirve como cola de comunicación asíncrona entre el resto de servicios, y por lo tanto los interconecta, y dispara la creación de servicios serverless como las Azure Function.

**Servicio en Azure:**  bordersens

**Colas:** El sistema de comunicación usado son colas FIFO. Esto implica que los mensajes se consumen según su antigüedad (primero los mas antiguos) y se eliminan al ser consumidos, por lo que solo pueden procesarse 1 vez. En este caso la existencia de un nuevo mensaje en una cola disparara la activación de una Function, destinada a procesarlo. Esto hace que el sistema pueda tener una altísima escalabilidad, ya que no hay limite (o es altísimo) de Functions desplegadas en paralelo. Actualmente existen 3 colas:

- **device_msg_in:**  Esta cola es el punto de entrada a partir del cual los mensajes entrantes en el servicio Azure IoT Hub, son introducidos en la misma. Basicamente en esta cola podemos encontrar los mensajes en bruto generados por los dispositivos e ingestados por el servicio IoT Hub. Estos mensajes son procesados por la Function **bordersens-msg-etl-function**, que realiza las transformaciones de los datos, para adecuarlos al formato que espera la Function de inferencia **bordersens-inference-function**.
- **inference_data:**  Los datos procesados por la Function **bordersens-msg-etl-function** que realiza las transformaciones sobre los datos enviados por los sensores, son escritos en esta cola, y procesados por la Function **bordersens-inference-function** que realiza la inferencia.
- **inference_response:**  Los datos procesados por la Function **bordersens-inference-function**, es decir los resultados de el modelo de IA, que realiza la detección de drogas, son escritos en esta cola, que servirá para que una aplicación pueda mostrarlos.

### Azure Funtions

Las Functions, son los servicios serverless, activados asíncronamente por eventos (en este caso la introducción de un nuevo mensaje en una cola de el Azure Service Bus),  que manejan la lógica de negocio, necesaria para hacer las predicciones, a partir de los datos, enviados desde los dispositivos, y gestionar la respuesta.

La gran ventaja del uso de funtions, en un patrón de comunicación asíncrona, es la altísima escalabilidad de la solución y la tolerancia a fallos. 

Se ha buscado también encapsular la lógica de la aplicación en distintas function, de forma que se maximice la mantenibilidad, es decir solo tengamos que hacer cambios, en la function, en la cual queremos mejorar su lógica de negocio, sin afectar al resto.

En este caso (por el momento), se han creado 2 Functions, que se describirán a continuación:

##### Funtion para logica de ETL (bordersens-msg-etl-function)

Esta function encapsula la lógica de las transformaciones necesarias, a partir de los datos en crudo enviados por los sensores, para que estos puedan ser ingeridos por los modelos de IA para realizar la inferencia. La función es disparada, cada vez que un nuevo evento llega a la cola **device_msg_in** y el dato transformado es escrito en la cola **inference_data**, para que sea consumido por el modelo.

##### Funtion para Inferencia (bordersens-inference-function)

Esta function encapsula la lógica para realizar la inferencia a partir del modelo entrenado, consumiendo los datos disponibles en la cola **inference_data** y propagando los resultados en la cola **inference_response**.

## Uso de la librería BorderSens SDK

Para usar la librería, lo único que debemos hacer es usar el jar generado [BorderSensSDK-1.0.0-jar-with-dependencies.jar](./BorderSensSDK-1.0.0-jar-with-dependencies.jar).

Esta diseñada para gestionar la interacción con el servicio [Azure IoT Hub](#Azure IoT Hub) descrito anteriormente, y que actúa como punto de entrada de los   datos hacia la plataforma.

Para ello, a alto nivel ofrece 3, funcionalidades:

### Gestionar el alta de dispositivos (sensores) en la plataforma.

Esta funcionalidad busca interactuar con el servicio [Azure IoT Hub Device Provisioning Service (DPS)](#Azure IoT Hub Device Provisioning Service (DPS)) para dar de alta los sensores en la plataforma y a partir de este punto poder realizar envíos de muestras para obtener los resultados.

Es importante indicar que el proceso es idempotente, es decir, el resultado será el mismo (el alta del dispositivo en el sistema), independientemente del numero de veces que se invoque. Por ejemplo, si ya di de alta el dispositivo, e invoco esta funcionalidad, no habrá cambios en la plataforma.

Para poder dar de alta un dispositivo en la plataforma es necesario, haber generado los certificados apropiados para el dispositivo, para ello, se recomienda leer la documentación [Creación de certificados X509 autofirmados](../generación de certificados/Creación de certificados X509 autofirmados.md)

### Gestionar el envío de mensajes hacia la plataforma.

### Gestionar la recepción de mensajes desde la plataforma.