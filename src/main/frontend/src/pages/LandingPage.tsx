import React from 'react';
import { Shield, Settings, BarChart3, Mail, Globe, Cpu } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

const LandingPage = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-white font-sans text-gray-900">
      {/* Navigation */}
      <nav className="flex items-center justify-between px-8 py-6 border-b">
        <div className="flex items-center space-x-2">
          <Cpu className="h-8 w-8 text-blue-600" />
          <span className="text-2xl font-bold tracking-tight text-blue-900">Plexus</span>
        </div>
        <div className="hidden md:flex space-x-8 font-medium">
          <a href="#features" className="hover:text-blue-600 transition">Features</a>
          <a href="#solutions" className="hover:text-blue-600 transition">Solutions</a>
          <a href="#contact" className="hover:text-blue-600 transition">Contact Us</a>
        </div>
        <div className="flex items-center space-x-4">
          <button
            onClick={() => navigate('/login-view')}
            className="bg-blue-600 text-white px-6 py-2 rounded-full font-semibold hover:bg-blue-700 transition"
          >
            Login
          </button>
        </div>
      </nav>

      {/* Hero Section */}
      <header className="px-8 py-20 bg-gradient-to-br from-blue-50 to-white text-center">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-5xl md:text-6xl font-extrabold text-blue-900 mb-6">
            The Flexible Engine for Modern Banking
          </h1>
          <p className="text-xl text-gray-600 mb-10 leading-relaxed">
            Define, manage, and calculate complex global banking products and pricing rules with ease.
            A component-based architecture designed for speed and scale.
          </p>
          <div className="flex flex-col md:flex-row justify-center space-y-4 md:space-y-0 md:space-x-4">
            <button
              onClick={() => window.open('/swagger-ui.html', '_blank')}
              className="bg-white border-2 border-blue-600 text-blue-600 px-8 py-3 rounded-full font-bold hover:bg-blue-50 transition"
            >
              Explore API
            </button>
            <button
              onClick={() => navigate('/onboarding')}
              className="bg-blue-600 text-white px-8 py-3 rounded-full font-bold hover:bg-blue-700 transition shadow-lg"
            >
              Get Started
            </button>
          </div>
        </div>
      </header>

      {/* Features Section */}
      <section id="features" className="px-8 py-20 max-w-7xl mx-auto">
        <div className="text-center mb-16">
          <h2 className="text-3xl font-bold text-blue-900 mb-4">Powerful Features</h2>
          <p className="text-gray-600 max-w-2xl mx-auto">Our platform provides everything you need to manage a complex product catalog in a multi-tenant environment.</p>
        </div>

        <div className="grid md:grid-cols-3 gap-12">
          <div className="p-6 rounded-2xl bg-white border hover:shadow-xl transition">
            <Settings className="h-12 w-12 text-blue-600 mb-6" />
            <h3 className="text-xl font-bold mb-3">Product Catalog</h3>
            <p className="text-gray-600">Reusable feature components allow you to define any banking product—CASA, Loans, Credit Cards—in minutes.</p>
          </div>

          <div className="p-6 rounded-2xl bg-white border hover:shadow-xl transition">
            <BarChart3 className="h-12 w-12 text-blue-600 mb-6" />
            <h3 className="text-xl font-bold mb-3">Dynamic Pricing</h3>
            <p className="text-gray-600">Complex tiered pricing, fees, and interest rates managed through a powerful rules engine integration.</p>
          </div>

          <div className="p-6 rounded-2xl bg-white border hover:shadow-xl transition">
            <Shield className="h-12 w-12 text-blue-600 mb-6" />
            <h3 className="text-xl font-bold mb-3">Enterprise Security</h3>
            <p className="text-gray-600">Granular RBAC with OIDC integration (EntraID, Keycloak). Multi-tenant isolation at the data level.</p>
          </div>
        </div>
      </section>

      {/* Multi-tenancy Section */}
      <section id="solutions" className="px-8 py-20 bg-blue-900 text-white">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center">
          <div className="md:w-1/2 mb-10 md:mb-0">
            <h2 className="text-4xl font-bold mb-6">Built for Global Institutions</h2>
            <p className="text-blue-100 text-lg mb-8 leading-relaxed">
              Whether you're a SaaS provider or a single-bank operation, Plexus adapts to your needs.
              Onboard multiple banks, each with their own identity providers and business rules,
              all while maintaining strict data isolation.
            </p>
            <ul className="space-y-4">
              <li className="flex items-center">
                <Globe className="h-6 w-6 mr-3 text-blue-400" />
                <span>Multi-currency & Global Support</span>
              </li>
              <li className="flex items-center">
                <Shield className="h-6 w-6 mr-3 text-blue-400" />
                <span>Regulatory Compliance Ready</span>
              </li>
            </ul>
          </div>
          <div className="md:w-1/2 md:pl-20">
            <div className="bg-blue-800 p-8 rounded-3xl shadow-2xl border border-blue-700">
              <pre className="text-sm text-blue-200 overflow-x-auto">
{`{
  "bankId": "GLOBAL-BANK-001",
  "issuerUrl": "https://login.microsoft.com/...",
  "allowProductInMultipleBundles": true,
  "categoryConflictRules": [
    { "categoryA": "RETAIL", "categoryB": "WEALTH" }
  ]
}`}
              </pre>
            </div>
          </div>
        </div>
      </section>

      {/* Contact Section */}
      <section id="contact" className="px-8 py-20 max-w-4xl mx-auto text-center">
        <Mail className="h-16 w-16 text-blue-600 mx-auto mb-6" />
        <h2 className="text-3xl font-bold text-blue-900 mb-4">Ready to Transform?</h2>
        <p className="text-xl text-gray-600 mb-10">
          Interested in onboarding your bank? Contact our sales team to request a demo and setup your tenant.
        </p>
        <a
          href="mailto:sales@plexus.com"
          className="inline-block bg-blue-600 text-white px-10 py-4 rounded-full font-bold text-lg hover:bg-blue-700 transition shadow-lg"
        >
          Contact Sales
        </a>
      </section>

      {/* Footer */}
      <footer className="px-8 py-12 border-t bg-gray-50">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center">
          <div className="flex items-center space-x-2 mb-4 md:mb-0">
            <Cpu className="h-6 w-6 text-blue-600" />
            <span className="text-xl font-bold tracking-tight text-blue-900">Plexus</span>
          </div>
          <p className="text-gray-500">© 2024 Plexus Bank Engine. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
};

export default LandingPage;
