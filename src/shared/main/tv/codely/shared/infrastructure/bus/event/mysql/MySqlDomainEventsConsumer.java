package tv.codely.shared.infrastructure.bus.event.mysql;

import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import tv.codely.shared.domain.Service;
import tv.codely.shared.domain.Utils;
import tv.codely.shared.domain.bus.event.DomainEvent;
import tv.codely.shared.infrastructure.bus.event.DomainEventsInformation;
import tv.codely.shared.infrastructure.bus.event.spring.SpringApplicationEventBus;

import javax.transaction.Transactional;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
public class MySqlDomainEventsConsumer {
    private final SessionFactory            sessionFactory;
    private final DomainEventsInformation   domainEventsInformation;
    private final SpringApplicationEventBus bus;
    private final Integer                   CHUNKS = 200;

    public MySqlDomainEventsConsumer(
        SessionFactory sessionFactory,
        DomainEventsInformation domainEventsInformation,
        SpringApplicationEventBus bus
    ) {
        this.sessionFactory          = sessionFactory;
        this.domainEventsInformation = domainEventsInformation;
        this.bus                     = bus;
    }

    @Transactional
    public void consume() {
        NativeQuery query = sessionFactory.getCurrentSession().createSQLQuery(
            "SELECT * FROM domain_events ORDER BY occurred_on ASC LIMIT 100;"
        );

        List<Object[]> events = query.list();

        try {
            for (Object[] event : events) {
                executeSubscribers(
                    (String) event[0],
                    (String) event[1],
                    (String) event[2],
                    (String) event[3],
                    (Timestamp) event[4]
                );
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    private void executeSubscribers(
        String id, String aggregateId, String eventName, String body, Timestamp occurredOn
    ) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        Class<? extends DomainEvent> domainEventClass = domainEventsInformation.forName(eventName);

        DomainEvent nullInstance = domainEventClass.getConstructor().newInstance();

        Method fromPrimitivesMethod = domainEventClass.getMethod(
            "fromPrimitives",
            String.class,
            HashMap.class,
            String.class,
            String.class
        );

        Object domainEvent = fromPrimitivesMethod.invoke(
            nullInstance,
            aggregateId,
            Utils.jsonDecode(body),
            id,
            Utils.dateToString(occurredOn)
        );

        bus.publish(Collections.singletonList((DomainEvent<?>) domainEvent));
    }
}
