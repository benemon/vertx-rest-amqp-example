package io.openshift.booster.messaging;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.NamingException;

import io.openshift.common.CommonConstants;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.kubernetes.KubernetesServiceImporter;

public class AMQProducerVerticle extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger("AMQProducerVerticle");

	private static final String AMQ_DEFAULT_HOST = "localhost";
	private static final int AMQP_DEFAULT_PORT = 5672;
	private static final String AMQP_DEFAULT_ADDRESS = "aTopic";

	private static final String SENT = "Sent: %s";

	private String amqBrokerHost;
	private int amqBrokerPort;
	private String amqBrokerUsername;
	private String amqBrokerPassword;
	private String amqBrokerAddress;

	private String kubernetesProject;

	private static final int DELIVERY_MODE = DeliveryMode.NON_PERSISTENT;

	public AMQProducerVerticle() {
		amqBrokerHost = System.getenv(CommonConstants.AMQP_BROKER_HOST_ENV) != null
				? System.getenv(CommonConstants.AMQP_BROKER_HOST_ENV) : AMQ_DEFAULT_HOST;

		amqBrokerPort = System.getenv(CommonConstants.AMQP_BROKER_PORT_ENV) != null
				? Integer.parseInt(System.getenv(CommonConstants.AMQP_BROKER_PORT_ENV)) : AMQP_DEFAULT_PORT;

		amqBrokerAddress = System.getenv(CommonConstants.AMQP_BROKER_ADDRESS_ENV) != null
				? System.getenv(CommonConstants.AMQP_BROKER_ADDRESS_ENV) : AMQP_DEFAULT_ADDRESS;

		amqBrokerUsername = System.getenv(CommonConstants.AMQP_BROKER_USER_ENV);

		amqBrokerPassword = System.getenv(CommonConstants.AMQP_BROKER_PASSWORD_ENV);

		kubernetesProject = System.getenv(CommonConstants.KUBERNETES_PROJECT_ENV);

	}

	@Override
	public void start() throws Exception {

		super.start();
		ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

		discovery.registerServiceImporter(new KubernetesServiceImporter(),
				new JsonObject().put("namespace", kubernetesProject));

		MessageConsumer<JsonObject> ebConsumer = vertx.eventBus()
				.consumer(CommonConstants.VERTX_EVENTBUS_DATA_ADDRESS_ENV);

		ebConsumer.handler(payload -> {

			log.info("Message received from event bus");

			discovery.getRecord(new JsonObject().put("name", "broker-amq-amqp"), ks -> {

				// Create the context env
				Hashtable<Object, Object> env = new Hashtable<Object, Object>();
				env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");

				if (ks.failed()) {
					log.warn(
							"Kubernetes service not found in the vert.x service registry. Either configure the Kubernetes service bridge, or assign environment variables for Broker configuration. ");

					env.put("connectionfactory.amqBrokerLookup", "amqp://" + amqBrokerHost + ":" + amqBrokerPort);
					env.put("queue.dataLookup", amqBrokerAddress);
					env.put("topic.liveDataLookup", amqBrokerAddress);

				} else {
					Record brokerRecord = ks.result();

					log.info("Kubernetes service found in the vert.x service registry: " + brokerRecord.toString());

					String brokerHost = brokerRecord.getLocation().getString("host");
					Integer brokerPort = brokerRecord.getLocation().getInteger("port");

					env.put("connectionfactory.amqBrokerLookup", "amqp://" + brokerHost + ":" + brokerPort);
					env.put("queue.dataLookup", amqBrokerAddress);
					env.put("topic.liveDataLookup", amqBrokerAddress);

				}

				try {
					// Create the context
					Context context = new javax.naming.InitialContext(env);
					produceMessage(payload, context);
				} catch (NamingException e) {
					log.error("Oops", e);
				}

			});

		});
	}

	private boolean produceMessage(io.vertx.core.eventbus.Message<JsonObject> payload, Context context) {
		boolean success = false;

		ConnectionFactory factory = null;
		Connection connection = null;
		Session session = null;
		MessageProducer messageProducer = null;
		Destination destination = null;

		try {

			factory = (ConnectionFactory) context.lookup("amqBrokerLookup");
			destination = (Destination) context.lookup("liveDataLookup");
			connection = factory.createConnection(amqBrokerUsername, amqBrokerPassword);
			connection.start();

			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			messageProducer = session.createProducer(destination);
			TextMessage message = session.createTextMessage(payload.body().encodePrettily());
			messageProducer.send(message, DELIVERY_MODE, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

			log.info(String.format(SENT, message));

		} catch (Exception e1) {
			log.error("Oops", e1);

		} finally {

			try {
				messageProducer.close();
				session.close();
				connection.close();
			} catch (JMSException jms) {
				log.error("Slightly bigger 'oops'", jms);
			}

		}

		return success;
	}

}
