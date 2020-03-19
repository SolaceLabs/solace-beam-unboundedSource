package com.solace.apache.beam;

import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;
import com.solacesystems.jcsmp.JCSMPStreamingPublishEventHandler;
import com.solacesystems.jcsmp.XMLMessageProducer;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.TestPipeline;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public abstract class ITBase {
	private static final Logger LOG = LoggerFactory.getLogger(SolaceIOIT.class);

	JCSMPSession jcsmpSession;
	XMLMessageProducer producer;
	SempOperationUtils sempOps;

	static JCSMPProperties testJcsmpProperties;
	static String mgmtHost;
	static String mgmtUsername;
	static String mgmtPassword;

	@BeforeClass
	public static void fetchPubSubConnectionDetails() {
		PipelineOptionsFactory.register(SolaceIOTestPipelineOptions.class);
		SolaceIOTestPipelineOptions options = TestPipeline.testingPipelineOptions().as(SolaceIOTestPipelineOptions.class);

		String solaceHostName = Optional.ofNullable(System.getenv("SOLACE_HOST")).orElse(options.getSolaceHostName());

		testJcsmpProperties = new JCSMPProperties();
		testJcsmpProperties.setProperty(JCSMPProperties.VPN_NAME,
				Optional.ofNullable(System.getenv("SOLACE_VPN_NAME")).orElse(options.getSolaceVpnName()));
		testJcsmpProperties.setProperty(JCSMPProperties.HOST, String.format("tcp://%s:%s", solaceHostName,
				Optional.ofNullable(System.getenv("SOLACE_SMF_PORT")).orElse(String.valueOf(options.getSolaceSmfPort()))));
		testJcsmpProperties.setProperty(JCSMPProperties.USERNAME,
				Optional.ofNullable(System.getenv("SOLACE_USERNAME")).orElse(options.getSolaceUsername()));
		testJcsmpProperties.setProperty(JCSMPProperties.PASSWORD,
				Optional.ofNullable(System.getenv("SOLACE_PASSWORD")).orElse(options.getSolacePassword()));

		testJcsmpProperties.setBooleanProperty(JCSMPProperties.GENERATE_SEQUENCE_NUMBERS, true);

		mgmtHost = String.format("https://%s:%s", solaceHostName,
				Optional.ofNullable(System.getenv("SOLACE_MGMT_PORT")).orElse(String.valueOf(options.getSolaceMgmtPort())));
		mgmtUsername = Optional.ofNullable(System.getenv("SOLACE_MGMT_USERNAME")).orElse(options.getSolaceMgmtUsername());
		mgmtPassword = Optional.ofNullable(System.getenv("SOLACE_MGMT_PASSWORD")).orElse(options.getSolaceMgmtPassword());
	}

	@Before
	public void setupConnection() throws Exception {
		LOG.info(String.format("Creating JCSMP Session for %s", testJcsmpProperties.getStringProperty(JCSMPProperties.HOST)));
		jcsmpSession = JCSMPFactory.onlyInstance().createSession(testJcsmpProperties);

		LOG.info(String.format("Creating XMLMessageProducer for %s", testJcsmpProperties.getStringProperty(JCSMPProperties.HOST)));
		producer = jcsmpSession.getMessageProducer(createPublisherEventHandler());

		sempOps = new SempOperationUtils(mgmtHost, mgmtUsername, mgmtPassword, jcsmpSession, false, true);
		sempOps.start();
	}

	@After
	public void teardownConnection() {
		if (jcsmpSession != null && !jcsmpSession.isClosed()) {
			if (sempOps != null) {
				sempOps.close();
			}

			if (producer != null) {
				producer.close();
			}

			LOG.info("Closing JCSMP Session");
			jcsmpSession.closeSession();
		}
	}

	static JCSMPStreamingPublishEventHandler createPublisherEventHandler() {
		return new JCSMPStreamingPublishEventHandler() {
			@Override
			public void responseReceived(String messageID) {
				LOG.debug("Producer received response for msg: " + messageID);
			}

			@Override
			public void handleError(String messageID, JCSMPException e, long timestamp) {
				LOG.warn("Producer received error for msg: " + messageID + " - " + timestamp, e);
			}
		};
	}
}
