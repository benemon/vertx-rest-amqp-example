package io.openshift.common;

public interface CommonConstants {

	public static final String AMQP_BROKER_USER_ENV = "BROKER_AMQ_AMQP_SERVICE_USER";
	public static final String AMQP_BROKER_PASSWORD_ENV = "BROKER_AMQ_AMQP_SERVICE_PASSWORD";
	public static final String AMQP_BROKER_HOST_ENV = "BROKER_AMQ_AMQP_SERVICE_HOST";
	public static final String AMQP_BROKER_PORT_ENV = "BROKER_AMQ_AMQP_SERVICE_PORT";
	public static final String AMQP_BROKER_ADDRESS_ENV = "BROKER_AMQ_AMQP_SERVICE_ADDRESS";

	public static final String VERTX_EVENTBUS_DATA_ADDRESS_ENV = "api.publish";

	public static final String KUBERNETES_PROJECT_ENV = "KUBERNETES_PROJECT";
}
