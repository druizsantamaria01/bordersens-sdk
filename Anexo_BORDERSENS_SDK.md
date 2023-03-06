# BorderSens SDK

Como norma general podemos decir que en el desarrollo de la SDK se ha optado por mantener la funcionalidad que exponía (por lo tanto los métodos enumerados en documentaciones precedentes, siguen estando accesibles), pero construir sobre dicha estructura una nueva capa de capa de abstracción, capaz de soportar las nuevas capacidades requeridas.
Se recomienda no obstante, actualizar todos los dispositivos para que funcionen a partir de esta nueva versión.

## Arquitectura y SDK
### Arquitectura desarrollada
La arquitectura implementada puede verse en el siguiente esquema


![](https://i.ibb.co/kS1CSjm/architecture-implemented-online-offline.png)
![](https://git.izertis.com/izertisidi/border_tec/bordersens-sdk/images/arquitectura-online-offline.png)


Como puede apreciarse, hay 2 modos de funcionamiento:

- **Modo Online:** Es el usado por defecto, cuando el **dispositivo tiene conectividad**. En este caso la petición se resuelve en la Plataforma BorderSens
- **Modo Offline:** Usado únicamente en situaciones de **no conectividad**

Hay algunos **componentes** importantes de la SDK que es conveniente describir

* **Monitor de conectividad:** Es el componente software encargado de detectar cambios de conectividad y propagarlos dentro de la SDK
* **Orquestador de contenedores:** Es el componente de software encargado de mantener siempre **desplegados, actualizados y activos** los contenedores docker desplegados dentro de la capa de arquitectura **BorderSens Local Plattform**. Entre las tareas asignadas a este componente software destacan las siguientes:
  1. Descargar imágenes de los contenedores desde el repositorio centralizado (Azure ACR).
  2. Desplegar los contenedores al iniciar la SDK.
  3. Monitorizar el estado de los contenedores, y redesplegarlos si sufren caídas.
  4. Comprobar periódicamente que el contendor desplegado, usa la última versión del contenedor, en caso contrario, para ese contenedor concreto, descargar la ultima imagen y volver al punto 1
* **Orquestador de datos:** Es el servicio encargado de sincronizar la base de datos local (MongoDB) usada en situaciones de no conectividad, con la base de datos remota disponible en la plataforma Azure (**BorderSens Azure Platform**).  Cabe mencionar que en este caso, la base de datos disponible en Azure (Cosmos), actúa siempre como Maestra. Para hacerlo realiza dos operaciones:
  * Operaciones de **subida** de datos desde la BBDD Local (MongoDB) a la base de datos remota (Cosmos): Al hacerlo sincroniza  los datos locales aun no sincronizados (datos en crudo de los dispositivos, datos transformados e Inferencia)  disponibles en la base de datos local a la base de datos remota (Cosmos).
  * Operaciones de **bajada** de datos desde la BBDD remota (Cosmos) a la base de datos local (Mongo): Al hacerlo sincroniza  los datos locales necesarios para las operaciones de transformación (parámetros de la ETL) y las operaciones de inferencia (parámetros del modelo como coeficientes y centroides) locales, haciendo que la plataforma local trabaje con las mismas versiones que la plataforma remota.
* **BorderSens Local Platform:** Es la plataforma virtualizada (usando docker), equivalente funcionalmente a la plataforma disponible en la nube (Azure Bordersens Platform) que será usada siempre en condiciones de no conectividad externa. El flujo de llamadas, a diferencia de la BorderSens Azure Platform, es siempre síncrono (petición - respuesta), dado que en este caso, no es necesario garantizar concurrencia y escalabilidad, ya que su funcionalidad se restringe al propio dispositivo. Despliega en su interior los siguientes contenedores:
  * **Bordersens-etl-api:** API desarrollada en Flask (Python), con la misión de procesar los datos en crudo enviados  por los dispositivos, chequear su integridad y transformarlos para que sean aptos para realizar la inferencia (corrección de línea base). Es un servicio con funcionalidad totalmente homónima a el servicio **Bordersens-ETL** desplegado en la nube Azure. 
  * **Bordersens-inference-api:** API desarrollada en Flask (Python), con la misión de a partir de los datos transformados por el servicio **bordersens-etl-api**, realizar la inferencia, usando la última versión de parámetros del modelo de inferencia. Es un servicio con funcionalidad totalmente homónima a el servicio **Bordersens-Inference** desplegado en la nube Azure. 
  * **Bordersens-mongo-db**: Base de datos mongo, que realiza la misma funcionalidad que la base de datos **Cosmos**, disponible en Azure. Para ello se sincroniza (en condiciones de conectividad), con la base de datos **Cosmos**, llevando a la plataforma en la nube, los datos locales aun no sincronizados (datos en crudo de los dispositivos, datos transformados e Inferencia) y actualizando los datos locales necesarios para las operaciones de transformación (parámetros de la ETL) y las operaciones de inferencia (parámetros del modelo como coeficientes y centroides). En ella de almacenan:
    * Datos en crudo
    * Datos procesados por la  ETL
    * Inferencia
    * Versiones de parámetros de etl
    * Versiones de modelos
* **BorderSens Azure Platform:** Es la plataforma en la nube (Azure). En este caso el flujo de llamadas,es siempre asíncrono, mediante colas de mensajes, dado que en este caso, es necesario garantizar concurrencia y escalabilidad, ya que su funcionalidad a de ser escalable a casi cualquier numero de dispositivos. Despliega en su interior los siguientes contenedores:
  * **Bordersens-etl:** Azure function desarrollada en Python, y desplegada ante un nuevo mensaje en el bus de datos, con la misión de procesar los datos en crudo enviados  por los dispositivos, chequear su integridad y transformarlos para que sean aptos para realizar la inferencia (corrección de línea base). 
  * **Bordersens-inference:** Azure function desarrollada en Python, y desplegada ante un nuevo mensaje en el bus de datos, realizar la inferencia, usando los datos procesados por Bordersens-etl y  la última versión de parámetros del modelo de inferencia. 
  * **Cosmos-db**: Es la base de datos maestra del proyecto Bordersens. En ella de almacenan:
    * Datos en crudo
    * Datos procesados por la  ETL
    * Inferencia
    * Versiones de parámetros de etl
    * Versiones de modelos
* **Online Handler:** Servicio que gestiona la peticiones en situaciones de conectividad, invocando a la plataforma **BorderSens Azure Platform**
* **Offline Handler:** Servicio que gestiona la peticiones en situaciones de nula conectividad, invocando a la plataforma **BorderSens Local Platform**
* **BorderSens SDK:** Módulo principal de la librería sobre el cual deberían de centralizarse todas las peticiones de usuario, y la recepción de eventos.

## Modo Online y Modo Offline

La elección de un modo u otro, **es automaticamente seleccionado por ls SDK**, en función de la conectividad del dispositivo.
Para ello, se ha creado un monitor de conectividad con capacidad de detectar cambios en la conectividad, de forma que la SDK, siempre es consciente de su estado.
Las peticiones se realizarán de forma única a la SDK, y esta las resolverá por la vía online o offline, según proceda de forma autónoma.

### Modo Online

En este caso, la SDK interactúa con la plataforma **BorderSens Azure Platform**. Es el modo preferible de uso, ya que esto garantiza de forma determinista, que todos los dispositivos, usan las mismas versiones de los algoritmos de transformación e inferencia.
Ademas se garantiza que los datos son almacenados en condiciones de seguridad óptimas en un repositorio centralizado.

### Modo Offline

En este caso, la SDK interactúa con la plataforma **BorderSens Local Platform**. Es el modo alternativo de uso, ya que no puede garantizarse que siempre use la última versión de modelos o parámetros (estos podrían haber cambiado en el periodo de desconexión y no serán actualizados hasta que el dispositivo disponga de conectividad).

### Comparativa modo On-line y modo Off-line

Desde un punto funcional, ambos modos deben de ofrecer un resultado similar, aunque hay aspectos que conviene tener presentes

|                               | Modo On-line                                                 | Modo Off-line                                                 |
| ----------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| Seguridad                     | Los datos están en todo momento **cifrados** tanto en reposo como en transito. La existencia de un **único repositorio** centralizado es preferible (desde el punto de vista de la seguridad) a la existencia de múltiples repositorios. | Los datos están **cifrados** en reposo y en transito, pero la existencia de **múltiples repositorios** (uno por dispositivo), hace que los datos estén sensiblemente mas expuestos. En cualquier caso, los datos solo permanecen en el dispositivo el tiempo necesario para sincronizarse con el repositorio centralizado. |
| Determinismo                  | En este caso, **se puede garantizar** que todos los datos, han sido procesados por una misma versión de transformaciones y/o Modelos de inferencia, en un determinado periodo de tiempo. | En este caso, **no se puede garantizar** que todos los datos, han sido procesados por una misma versión de transformaciones y/o Modelos de inferencia, en un determinado periodo de tiempo, aunque la librería intentara mantener las mismas versiones, en los casos en los que no exista conectividad por un periodo prolongado de tiempo, las versiones podrían haber evolucionado, y estas no podrán ser sincronizadas, hasta que el dispositivo tenga conectividad, lo que deriva que potencialmente en el mismo periodo de tiempo, pueden existir dispositivos trabajando en distintas versiones. |
| Dependencias                  | En este caso la única dependencia para obtener un resultado es la **conectividad**. | En este caso la única dependencia para obtener un resultado es disponer de una instancia de  **docker engine** en el dispositivo. |
| Tipo de peticiones y recursos | En este caso, las peticiones son **asíncronas**, lo que deriva en una escalabilidad prácticamente ilimitada, en la plataforma y unos tiempos de latencia, **independientes de la capacidad de la máquina**. | En este caso, las peticiones son **síncronas**, ya no es necesario garantizar la escalabilidad, para atender a un solo dispositivo y unos tiempos de latencia, **dependientes dé la capacidad de la máquina**. |

## Manual de uso

En este caso toda la documentación anterior disponible en el [README.md](./README.md) sigue estando vigente, ya que para la nueva versión se han usado los componentes allí descritos, pero ya que los nuevos componentes solo están descritos en este documento, se recomienda solo el uso del mismo.

### Dependencias (importante)

Dado que para implementar la plataforma localmente, es necesario el uso de contenedores docker, es necesario un proceso **docker engine** ejecutándose en la misma máquina que haga uso de la librería.

Podemos instalarlo fácilmente siguiendo los pasos enumerados en la [documentación oficial](https://docs.docker.com/engine/install/) , ya sea sobre SO Linux o Windows.

### Get Started

La librería es bastante autonoma, es decir realizara las funciones requeridas con casi ninguna interación del usuario.

Podemos descargar el jar de la librería en el siguiente [enlace](./sdk/BorderSensSDK-1.0.0-jar-with-dependencies.jar).

**Es necesario que al menos en la primera inicialización de la librería esta disponga de conectividad**, ya que realizara acciones que la requieren como pro ejemplo, la descarga de imágenes docker.

Para interactuar con ella, solo tenemos que seguir los siguientes pasos

#### Obtención de instancia

Debemos obtener una instancia de la librería. Podemos hacerlo de 2 formas:

* Sin subscribirse a los eventos que la librería propaga

```java
BorderSensSDKService sdk  = BorderSensSDKService.getInstance(idDevice);
```

* Con subscripción a los eventos de la librería

```java
        BorderSensSDKService sdk  = BorderSensSDKService.getInstance(idDevice, new BorderSensSDKServiceEvents() {
            @Override
            public void onDockerStateChange(boolean b) {
                // Eventos cuando el servicio docker cambia de estado
            }

            @Override
            public void onDockerContainerIsDeployed(String s, DeployContainerStatus deployContainerStatus, ContainerStatus containerStatus) {
				// Eventos cuando un determinado contenedor docker cambia de estado
            }

            @Override
            public void OnDataSynchronizedIsDone(JsonObject jsonObject) {
				// Eventos cuando termina una determinada sincronización de datos
            }

            @Override
            public void OnBorderSensSDKServiceIsInitialized(boolean b, boolean b1) {
				// Eventos cuando el servicio BorderSens SDK se ha inicializado
            }
        });
```



En cualquiera de los 2 casos, necesitamos pasar como parámetro el ID del dispositivo, que será fijado desde ese momento en la librería.

#### Inicialización

Posteriormente a la obtención de la instancia, es necesario inicializar la librería, esto derivará en que la librería realizara las siguientes acciones **de forma autónoma**:

* Conexión con docker y despliegue de contenedores. Esto implicara que la librería realice las siguientes acciones:
  1. Descarga de imágenes docker (solo en la primera inicialización, para el resto solo comprobara la versión y la descargara solo si hubiese modificaciones)
  2. Despliegue de contendores de acuerdo a las imágenes descargadas.
  3. Monitorizar el estado de los contenedores, y redesplegarlos si sufren caídas.
  4. Comprobar periódicamente que el contendor desplegado, usa la última versión del contenedor, en caso contrario, para ese contenedor concreto, descargar la ultima imagen y volver al punto 1
* Actualización de datos:
  * Descarga de últimas versiones de modelos y parámetros para realizar inferencia y transformación de datos.
  * Monitorización periódica de cambios en bases de datos local y remota, para realizar tareas de sincronización

Para realizar estas acciones solo hay que invocar el método **initialize** sobre la instancia obtenida anteriormente

```java
 sdk.initialize();
```

Al realizarlo este método realizara las acciones descritas, y podremos verifícalo en los logs

Dependiendo de si es la primera inicialización o no, y del estado de la conexión, esta invocación **puede tardar más o menos tiempo en retornar**, sin embargo es indispensable realizar estas operaciones para garantizar un estado consistente.

Adicionalmente podemos verificar el despliegue de los servicios docker ejecutando `docker ps` desde un terminal.

![](data:image/png;base64,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)

#### Envió de mensajes

 Como en la versión anterior, existe el método **sendSyncMessage** que permite enviar los datos a la plataforma Azure (si hay conectividad) o a la plataforma Local (si no la hay). Esto es totalmente transparente para el usuario. En cualquiera de los dos casos deberá de obtener la misma respuesta (idéntica a la versión anterior de la SDK).

```java
publicCertificate = certificatesPath + "\\device-2-bs-public.pem"; //path del certificado público";
privateCertificate = certificatesPath + "\\device-2-bs-private.pem"; //path del certificado privado";
SecurityProvider securityProvider = Utilities.getSecurityProviderX509(publicCertificate,privateCertificate);
String jMsgStr = ...; // Objeto del mensaje que queremos enviar en formato Json String

Map<String,Object> response = sdk.sendSyncMessage(securityProvider,jMsgStr); // Petición y obtención de respuesta
```

Respuesta

```json
{
  "id_device": "device-1-bs",
  "id_sample": "dae98764-0078-4a07-bb66-f8f546852d76",
  "steps": [
    {
      "stepName": "SEND",
      "datetime": "2022-11-17 16:05:39.039 +0100"
    },
    {
      "stepName": "RECEPTION",
      "datetime": "2023-03-01T15:20:51.297841"
    },
    {
      "stepName": "TRANSFORMAION",
      "datetime": "2023-03-01T15:20:52.072693"
    },
    {
      "stepName": "INFERENCE",
      "datetime": "2023-03-01T15:20:53.258801"
    }
  ],
  "error": null,
  "inference": "Clase1",
  "status": "OK"
}
```

