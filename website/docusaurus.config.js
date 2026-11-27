// @ts-check
// `@type` JSDoc annotations allow editor autocompletion and type checking
// (when paired with `@ts-check`).
// There are various equivalent ways to declare your Docusaurus config.
// See: https://docusaurus.io/docs/api/docusaurus-config

import {themes as prismThemes} from 'prism-react-renderer';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const GITHUB_ORG = 'rroyoo';
const GITHUB_REPO = 'mockjdbc-foundation';
const GITHUB_URL = `https://github.com/${GITHUB_ORG}/${GITHUB_REPO}`;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'MockJDBC',
  tagline: 'Record real JDBC traffic. Replay it anywhere, without a live database.',
  favicon: 'img/favicon.ico',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // Set the production url of your site here
  url: `https://${GITHUB_ORG}.github.io`,
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: `/${GITHUB_REPO}/`,

  // GitHub pages deployment config.
  organizationName: GITHUB_ORG, // Usually your GitHub org/user name.
  projectName: GITHUB_REPO, // Usually your repo name.
  trailingSlash: false,

  onBrokenLinks: 'throw',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  themes: ['@docusaurus/theme-mermaid', 'docusaurus-lunr-search'],
  markdown: {
    mermaid: true,
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          editUrl: `${GITHUB_URL}/tree/main/website/`,
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/docusaurus-social-card.jpg',
      colorMode: {
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'MockJDBC',
        logo: {
          alt: 'MockJDBC Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Docs',
          },
          {
            href: `${GITHUB_URL}/blob/main/docs/open-source-launch-roadmap.md`,
            label: 'Roadmap',
            position: 'left',
          },
          {
            href: GITHUB_URL,
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Introduction',
                to: '/docs/',
              },
              {
                label: 'Getting started',
                to: '/docs/getting-started/installation',
              },
              {
                label: 'End-to-end tutorial',
                to: '/docs/guides/end-to-end-tutorial',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'GitHub Discussions',
                href: `${GITHUB_URL}/discussions`,
              },
              {
                label: 'Issues',
                href: `${GITHUB_URL}/issues`,
              },
              {
                label: 'Contributing',
                to: '/docs/contributing',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'GitHub',
                href: GITHUB_URL,
              },
              {
                label: 'License (Apache 2.0)',
                href: `${GITHUB_URL}/blob/main/LICENSE`,
              },
              {
                label: 'Security policy',
                href: `${GITHUB_URL}/blob/main/SECURITY.md`,
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} MockJDBC contributors. Built with Docusaurus.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['java', 'bash', 'yaml', 'markup', 'protobuf'],
      },
    }),
};

export default config;
