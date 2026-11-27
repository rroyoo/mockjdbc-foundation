import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Record real traffic',
    Svg: require('@site/static/img/undraw_docusaurus_mountain.svg').default,
    description: (
      <>
        The <code>mockjdbc-proxy</code> Java agent instruments your real
        DataSource and captures SQL, parameters, and results as protobuf
        events — no code changes required.
      </>
    ),
  },
  {
    title: 'Replay without a database',
    Svg: require('@site/static/img/undraw_docusaurus_tree.svg').default,
    description: (
      <>
        <code>mockjdbc-mock</code> is a real <code>java.sql.Driver</code>{' '}
        (<code>jdbc:mock://...</code>) that replays captured traffic
        deterministically, so your application code runs unmodified.
      </>
    ),
  },
  {
    title: 'Built for debugging and CI',
    Svg: require('@site/static/img/undraw_docusaurus_react.svg').default,
    description: (
      <>
        MockJDBC is a debugging and testing tool, never a production
        dependency. JDBC coverage is tracked transparently — see the
        supported surface and known limitations.
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
