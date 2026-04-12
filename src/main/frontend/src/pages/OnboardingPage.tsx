import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation, useParams } from 'react-router-dom';
import { ShieldCheck, Rocket, Info, ArrowLeft, Loader2, Eye, EyeOff, Save, Trash2, Plus, Settings2 } from 'lucide-react';
import axios from 'axios';
import PlexusSelect from '../components/PlexusSelect';
import GlobalToast from '../components/GlobalToast';
import { useAuth } from '../context/AuthContext';
import { useBreadcrumb } from '../context/BreadcrumbContext';
import { useAbortSignal } from '../hooks/useAbortSignal';
import { AdminPage, AdminFormHeader } from '../components/AdminPageLayout';
import { Building2 } from 'lucide-react';

const CATEGORY_EXAMPLES = ['RETAIL', 'WEALTH', 'CORPORATE', 'INVESTMENT', 'ISLAMIC', 'SME'];
const CREATE_NEW_CATEGORY = 'CREATE_NEW';

const OnboardingPage = () => {
  const { user, setToast } = useAuth();
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const { setEntityName } = useBreadcrumb();

  const authorities = (user?.roles as string[]) || [];
  const isSystemAdmin = authorities.includes('SYSTEM_ADMIN');

  const isMyBank = location.pathname === '/my-bank';
  const isAdmin = (isMyBank || !!id || location.pathname === '/banks/create' || location.pathname.startsWith('/banks/edit/')) && (isSystemAdmin || isMyBank);
  const isEditing = (!!id && isSystemAdmin) || isMyBank;

  const [formData, setFormData] = useState<any>({
    bankId: '',
    name: '',
    issuerUrl: '',
    clientId: '',
    clientSecret: '',
    adminName: '',
    adminEmail: '',
    currencyCode: 'USD',
    captchaAnswer: '',
    allowProductInMultipleBundles: false,
    categoryConflictRules: []
  });

  const [customCurrency, setCustomCurrency] = useState('');
  const [isCustomCurrency, setIsCustomCurrency] = useState(false);
  const [isBankIdEdited, setIsBankIdEdited] = useState(false);
  const [captcha, setCaptcha] = useState({ question: '', id: '' });
  const [loading, setLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [showSecret, setShowSecret] = useState(false);
  const [categorySuggestions, setCategorySuggestions] = useState<string[]>([]);
  const [hasExistingCategories, setHasExistingCategories] = useState(false);
  const [customConflictInputs, setCustomConflictInputs] = useState<Record<string, string>>({});
  const signal = useAbortSignal();

  const fetchBankData = useCallback(async (abortSignal: AbortSignal) => {
    setLoading(true);
    try {
      const targetId = isMyBank ? user?.bank_id : id;
      if (!targetId) return;

      const response = await axios.get(`/api/v1/banks/${targetId}`, { signal: abortSignal });
      const data = response.data;
      setFormData({
        bankId: data.bankId,
        name: data.name,
        issuerUrl: data.issuerUrl,
        clientId: data.clientId,
        clientSecret: '', // Leave empty. Backend will only update if not blank.
        adminName: data.adminName,
        adminEmail: data.adminEmail,
        currencyCode: data.currencyCode,
        captchaAnswer: '',
        allowProductInMultipleBundles: data.allowProductInMultipleBundles || false,
        categoryConflictRules: data.categoryConflictRules || []
      });

      try {
        const categoryResponse = await axios.get(`/api/v1/banks/${targetId}/product-categories`, { signal: abortSignal });
        const existing = Array.isArray(categoryResponse.data?.categories) ? categoryResponse.data.categories : [];
        const examples = Array.isArray(categoryResponse.data?.examples) ? categoryResponse.data.examples : CATEGORY_EXAMPLES;
        const merged = Array.from(new Set([...existing, ...examples]));
        setCategorySuggestions(merged);
        setHasExistingCategories(existing.length > 0);
      } catch (_ignored) {
        setCategorySuggestions([]);
        setHasExistingCategories(false);
      }

      setEntityName(data.name);
      setIsBankIdEdited(true);
      const isCustom = data.currencyCode && !['USD', 'EUR', 'GBP', 'JPY'].includes(data.currencyCode);
      setIsCustomCurrency(isCustom);
      if (isCustom) {
        setCustomCurrency(data.currencyCode);
      }

      if (!isEditing) {
        setCategorySuggestions([]);
        setHasExistingCategories(false);
      }
    } catch (err) {
      if (axios.isCancel(err)) return;
      console.error('Failed to fetch bank data', err);
      setToast({ message: 'Failed to load bank configuration for editing.', type: 'error' });
    } finally {
      if (!abortSignal.aborted) {
        setLoading(false);
      }
    }
  }, [id, isMyBank, user?.bank_id, setEntityName, setToast, isEditing]);

  useEffect(() => {
    if (!isAdmin) {
      fetchCaptcha(signal);
    }

    if (isEditing) {
      fetchBankData(signal);
    }
  }, [id, isAdmin, isEditing, fetchBankData, signal]);

  const fetchCaptcha = async (abortSignal: AbortSignal) => {
    try {
      const response = await axios.get('/api/v1/public/onboarding/captcha', { signal: abortSignal });
      setCaptcha(response.data);
    } catch (err) {
      if (axios.isCancel(err)) return;
      console.error('Failed to fetch captcha');
    }
  };

  const validateForm = () => {
    const errors: Record<string, string> = {};

    if (!formData.name?.trim()) {
      errors.name = 'Bank Name is required.';
    }

    if (formData.adminName.length < 3 || formData.adminName.length > 50) {
      errors.adminName = 'Admin Name must be between 3 and 50 characters.';
    }

    if (!formData.adminEmail?.trim()) {
      errors.adminEmail = 'Admin Email is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.adminEmail)) {
      errors.adminEmail = 'Please enter a valid email address.';
    }

    const urlPattern = /^(https?:\/\/)[^\s$.?#].[^\s]*$/i;
    if (!urlPattern.test(formData.issuerUrl)) {
      errors.issuerUrl = 'Please enter a valid OIDC Issuer URL (starting with http:// or https://).';
    }

    if (!formData.clientId) {
      errors.clientId = 'Client ID is required.';
    } else {
      // Relaxed validation: Allow alphanumeric, hyphens, underscores and dots.
      const clientIdPattern = /^[a-zA-Z0-9._-]+$/i;
      if (!clientIdPattern.test(formData.clientId)) {
        errors.clientId = 'Client ID must be alphanumeric and may contain hyphens, underscores, or dots.';
      }
    }

    if (!isAdmin && !formData.captchaAnswer) {
      errors.captchaAnswer = 'Security answer is required.';
    }

    // Category Conflict Rules validation
    const seenPairs = new Set<string>();
    formData.categoryConflictRules.forEach((rule: any, idx: number) => {
      const catA = rule.categoryA?.trim();
      const catB = rule.categoryB?.trim();

      if (!catA || !catB) {
        errors[`conflictRule_${idx}`] = 'Both categories must be specified.';
      } else if (catA === catB) {
        errors[`conflictRule_${idx}`] = 'A category cannot conflict with itself.';
      } else {
        const pair = [catA, catB].sort().join('|');
        if (seenPairs.has(pair)) {
          errors[`conflictRule_${idx}`] = 'This conflict rule is a duplicate.';
        }
        seenPairs.add(pair);
      }
    });

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    if (!validateForm()) {
      setToast({ message: 'Please correct the highlighted errors.', type: 'error' });
      return;
    }
    setLoading(true);
    try {
      const bankDetails = {
        ...formData,
        currencyCode: isCustomCurrency ? customCurrency.toUpperCase() : formData.currencyCode
      };

      if (isMyBank) {
        await axios.put('/api/v1/banks', bankDetails);
      } else if (isEditing) {
        await axios.put(`/api/v1/banks/${id}`, bankDetails);
      } else if (isAdmin) {
        await axios.post('/api/v1/banks', bankDetails);
      } else {
        const submissionData = {
          bankDetails,
          captchaId: captcha.id,
          captchaAnswer: formData.captchaAnswer
        };
        await axios.post('/api/v1/public/onboarding', submissionData);
      }

      const successMsg = isEditing ? 'Bank configuration has been updated successfully!' :
      isAdmin ? 'New bank has been added successfully!' :
      'Your onboarding request has been submitted successfully! A system administrator will review your request shortly.';

      const successTitle = isEditing ? 'Update Successful!' : isAdmin ? 'Bank Created!' : 'Request Submitted!';

      setToast({ message: successMsg, type: 'success' });
      navigate(isAdmin ? '/banks' : '/', {
        state: {
          onboardingSuccess: true,
          title: successTitle,
          message: successMsg
        }
      });
    } catch (err: any) {
      const errorDetail = err.response?.data?.message || err.response?.data || 'Failed to submit request. Please check your inputs and captcha.';
      const finalError = typeof errorDetail === 'string' ? (errorDetail || 'Unknown error occurred.') : JSON.stringify(errorDetail);
      setToast({ message: finalError, type: 'error' });
      fetchCaptcha(signal); // Refresh captcha on error
    } finally {
      setLoading(false);
    }
  };

  const addConflictRule = () => {
    setFormData({
      ...formData,
      categoryConflictRules: [...formData.categoryConflictRules, { categoryA: '', categoryB: '' }]
    });
  };

  const getCustomConflictKey = (index: number, field: 'categoryA' | 'categoryB') => `${index}_${field}`;

  const buildCategoryOptions = (excluded?: string) => {
    const normalizedExcluded = excluded?.toUpperCase();
    const base = categorySuggestions
      .map((category) => category.toUpperCase())
      .filter((category) => category && category !== normalizedExcluded);
    return [...base, CREATE_NEW_CATEGORY];
  };

  const buildCategorySelectOptions = (excluded?: string) => {
    return buildCategoryOptions(excluded).map((value) => ({ value, label: value }));
  };

  const isCustomCategory = (value: string, excluded?: string) => {
    if (!value) return false;
    return !buildCategoryOptions(excluded).includes(value.toUpperCase());
  };


  const removeConflictRule = (index: number) => {
    const newRules = [...formData.categoryConflictRules];
    newRules.splice(index, 1);
    setCustomConflictInputs((prev) => {
      const updated = { ...prev };
      delete updated[getCustomConflictKey(index, 'categoryA')];
      delete updated[getCustomConflictKey(index, 'categoryB')];
      return updated;
    });
    setFormData({ ...formData, categoryConflictRules: newRules });
  };

  const updateConflictRule = (index: number, field: 'categoryA' | 'categoryB', value: string) => {
    const newRules = [...formData.categoryConflictRules];
    const normalized = value.toUpperCase().trim().replace(/\s+/g, '_');
    newRules[index] = { ...newRules[index], [field]: normalized };

    // Keep rule coherent: if one side changes to same value as the other, clear the other side.
    const otherField = field === 'categoryA' ? 'categoryB' : 'categoryA';
    if (normalized && newRules[index][otherField] === normalized) {
      newRules[index][otherField] = '';
    }

    setFormData({ ...formData, categoryConflictRules: newRules });
  };

  const onConflictCategorySelect = (index: number, field: 'categoryA' | 'categoryB', selected?: { value: string } | null) => {
    const selectedValue = selected?.value || '';
    const customKey = getCustomConflictKey(index, field);

    if (selectedValue === CREATE_NEW_CATEGORY) {
      const current = formData.categoryConflictRules[index]?.[field] || '';
      setCustomConflictInputs((prev) => ({ ...prev, [customKey]: current }));
      updateConflictRule(index, field, current);
      return;
    }

    setCustomConflictInputs((prev) => {
      const updated = { ...prev };
      delete updated[customKey];
      return updated;
    });
    updateConflictRule(index, field, selectedValue);
  };

  const onConflictCustomInputChange = (index: number, field: 'categoryA' | 'categoryB', rawValue: string) => {
    const customKey = getCustomConflictKey(index, field);
    const normalized = rawValue.toUpperCase().replace(/\s+/g, '_');
    setCustomConflictInputs((prev) => ({ ...prev, [customKey]: normalized }));
    updateConflictRule(index, field, normalized);
  };

  const renderForm = () => (
    <div className={isAdmin ? "" : "max-w-xl w-full mx-auto"}>
      {!isAdmin && <GlobalToast />}
      {!isAdmin && (
        <>
          <button
            onClick={() => navigate('/')}
            className="flex items-center text-blue-600 hover:text-blue-800 font-bold mb-4 text-sm transition"
          >
            <ArrowLeft className="h-4 w-4 mr-1.5" />
            Back to Home
          </button>

          <div className="flex justify-between items-start mb-1">
            <h2 className="text-xl font-bold text-gray-900">
              Get Started
            </h2>
          </div>
          <p className="text-xs text-gray-500 mb-6 font-medium">
            Fill out the form below to submit an onboarding request for your institution.
          </p>
        </>
      )}

      <form onSubmit={handleSubmit} className={isAdmin ? "space-y-6" : "space-y-4"}>
        <div className="space-y-4">
          <div className="flex items-center space-x-2 text-blue-900 mb-2">
            <Info className="h-4 w-4" />
            <h3 className="text-xs font-bold uppercase tracking-widest">Core Configuration</h3>
          </div>
          <div>
              <label className="block text-xs font-bold text-gray-700 mb-1.5">Bank Name</label>
              <input
                disabled={isMyBank && !isSystemAdmin}
                type="text"
                placeholder="e.g. Global Bank"
                className={`w-full px-3 py-2 rounded-lg border focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none text-sm transition ${(isMyBank && !isSystemAdmin) ? 'bg-gray-50 text-gray-500' : ''} ${fieldErrors.name ? 'border-red-500' : 'border-gray-300'}`}
                value={formData.name}
                onChange={e => {
                  const name = e.target.value;
                  let bankId = formData.bankId;
                  if (!isEditing && !isBankIdEdited) {
                    bankId = name.toUpperCase().trim().replace(/\s+/g, '_').replace(/[^A-Z0-9_-]/g, '');
                  }
                  setFormData({ ...formData, name, bankId });
                  if (fieldErrors.name) setFieldErrors({ ...fieldErrors, name: '' });
                }}
              />
              {fieldErrors.name && <p className="mt-1 text-[10px] text-red-500 font-bold">{fieldErrors.name}</p>}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1.5">Bank ID {isEditing ? '(Read Only)' : '(Generated)'}</label>
              <input
                disabled={isEditing}
                type="text"
                placeholder="e.g. GLOBAL-BANK-001"
                className={`w-full px-3 py-2 rounded-lg border border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-transparent outline-none text-sm transition ${isEditing ? 'bg-gray-50 text-gray-500' : ''}`}
                value={formData.bankId}
                onChange={e => {
                  setIsBankIdEdited(true);
                  setFormData({ ...formData, bankId: e.target.value.toUpperCase().replace(/\s/g, '_') });
                }}
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1.5">Currency</label>
              <div className="relative">
                {(!isCustomCurrency) ? (
                    <PlexusSelect
                      isDisabled={isMyBank && !isSystemAdmin}
                      options={[
                        { value: 'USD', label: 'USD - US Dollar' },
                        { value: 'EUR', label: 'EUR - Euro' },
                        { value: 'GBP', label: 'GBP - British Pound' },
                        { value: 'JPY', label: 'JPY - Japanese Yen' },
                        { value: 'OTHER', label: 'Other (Enter code...)' }
                      ]}
                      value={['USD', 'EUR', 'GBP', 'JPY'].includes(formData.currencyCode) ? {
                        value: formData.currencyCode,
                        label: ({ USD: 'USD - US Dollar', EUR: 'EUR - Euro', GBP: 'GBP - British Pound', JPY: 'JPY - Japanese Yen' } as any)[formData.currencyCode]
                      } : null}
                      onChange={opt => {
                        if (opt?.value === 'OTHER') {
                          setIsCustomCurrency(true);
                        } else if (opt) {
                          setFormData({ ...formData, currencyCode: opt.value });
                        }
                      }}
                    />
                ) : (
                  <div className="flex space-x-2">
                    <input
                      autoFocus
                      disabled={isMyBank && !isSystemAdmin}
                      type="text"
                      placeholder="e.g. AUD"
                      className={`flex-1 px-3 py-2 rounded-lg border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none text-sm transition ${(isMyBank && !isSystemAdmin) ? 'bg-gray-50 text-gray-500' : ''}`}
                      value={customCurrency}
                      onChange={e => setCustomCurrency(e.target.value.toUpperCase())}
                    />
                    {(!isMyBank || isSystemAdmin) && (
                      <button
                        type="button"
                        onClick={() => setIsCustomCurrency(false)}
                        className="px-3 text-sm text-blue-600 hover:text-blue-800"
                      >
                        Reset
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-700 mb-0.5">OIDC Issuer URL</label>
            <p className="text-[10px] text-gray-500 mb-1.5 font-medium italic">
              The discovery URL of your Identity Provider.
            </p>
            <input
              disabled={isMyBank && !isSystemAdmin}
              type="text"
              placeholder="https://login.microsoftonline.com/..."
              className={`w-full px-3 py-2 rounded-lg border focus:ring-2 focus:ring-blue-500 outline-none text-sm transition ${(isMyBank && !isSystemAdmin) ? 'bg-gray-50 text-gray-500' : ''} ${fieldErrors.issuerUrl ? 'border-red-500' : 'border-gray-300'}`}
              value={formData.issuerUrl}
              onChange={e => {
                setFormData({ ...formData, issuerUrl: e.target.value });
                if (fieldErrors.issuerUrl) setFieldErrors({ ...fieldErrors, issuerUrl: '' });
              }}
            />
            {fieldErrors.issuerUrl && <p className="mt-1 text-[10px] text-red-500 font-bold">{fieldErrors.issuerUrl}</p>}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-gray-700 mb-0.5">Client ID</label>
              <p className="text-[10px] text-gray-500 mb-1.5 font-medium italic">
                The Application (client) ID registered in your IDP.
              </p>
              <input
                disabled={isMyBank && !isSystemAdmin}
                type="text"
                placeholder="e.g. 12345678-...90ab"
                className={`w-full px-3 py-2 rounded-lg border focus:ring-2 focus:ring-blue-500 outline-none text-sm transition ${(isMyBank && !isSystemAdmin) ? 'bg-gray-50 text-gray-500' : ''} ${fieldErrors.clientId ? 'border-red-500' : 'border-gray-300'}`}
                value={formData.clientId}
                onChange={e => {
                  setFormData({ ...formData, clientId: e.target.value });
                  if (fieldErrors.clientId) setFieldErrors({ ...fieldErrors, clientId: '' });
                }}
              />
              {fieldErrors.clientId && <p className="mt-1 text-[10px] text-red-500 font-bold">{fieldErrors.clientId}</p>}
            </div>
            {(!isMyBank || isSystemAdmin) && (
              <div>
                <label className="block text-xs font-bold text-gray-700 mb-0.5">Client Secret (Optional)</label>
                <p className="text-[10px] text-gray-500 mb-1.5 font-medium italic">
                  The Application Secret if your IDP requires it.
                </p>
                <div className="relative">
                  <input
                    type={showSecret ? "text" : "password"}
                    placeholder="••••••••••••"
                    className="w-full px-3 py-2 rounded-lg border border-gray-300 focus:ring-2 focus:ring-blue-500 outline-none text-sm transition pr-10"
                    value={formData.clientSecret}
                    onChange={e => setFormData({ ...formData, clientSecret: e.target.value })}
                  />
                  <button
                    type="button"
                    onClick={() => setShowSecret(!showSecret)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 p-2 hover:bg-gray-100 rounded-lg text-gray-400"
                  >
                    {showSecret ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-3">
            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1.5">Admin Name</label>
              <input
                disabled={isMyBank && !isSystemAdmin}
                type="text"
                placeholder="Full Name"
                className={`w-full px-3 py-2 rounded-lg border focus:ring-2 focus:ring-blue-500 outline-none text-sm transition ${(isMyBank && !isSystemAdmin) ? 'bg-gray-50 text-gray-500' : ''} ${fieldErrors.adminName ? 'border-red-500' : 'border-gray-300'}`}
                value={formData.adminName}
                onChange={e => {
                  setFormData({ ...formData, adminName: e.target.value });
                  if (fieldErrors.adminName) setFieldErrors({ ...fieldErrors, adminName: '' });
                }}
              />
              {fieldErrors.adminName && <p className="mt-1 text-[10px] text-red-500 font-bold">{fieldErrors.adminName}</p>}
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1.5">Admin Email</label>
              <input
                disabled={isMyBank && !isSystemAdmin}
                type="email"
                placeholder="email@bank.com"
                className={`w-full px-3 py-2 rounded-lg border focus:ring-2 focus:ring-blue-500 outline-none text-sm transition ${(isMyBank && !isSystemAdmin) ? 'bg-gray-50 text-gray-500' : ''} ${fieldErrors.adminEmail ? 'border-red-500' : 'border-gray-300'}`}
                value={formData.adminEmail}
                onChange={e => {
                  setFormData({ ...formData, adminEmail: e.target.value });
                  if (fieldErrors.adminEmail) setFieldErrors({ ...fieldErrors, adminEmail: '' });
                }}
              />
              {fieldErrors.adminEmail && <p className="mt-1 text-[10px] text-red-500 font-bold">{fieldErrors.adminEmail}</p>}
            </div>
          </div>
        </div>

        {/* Advanced Configuration Section */}
        {isAdmin && (
          <div className="pt-6 border-t border-gray-100 space-y-6">
            <div className="flex items-center space-x-2 text-blue-900">
              <Settings2 className="h-4 w-4" />
              <h3 className="text-xs font-bold uppercase tracking-widest">Advanced Engine Configuration</h3>
            </div>

            <div className="bg-blue-50/50 p-4 rounded-xl border border-blue-100">
              <div className="flex items-center justify-between">
                <div>
                  <label className="block text-xs font-bold text-gray-900">Allow Product in Multiple Bundles</label>
                  <p className="text-[10px] text-blue-700 font-medium">If enabled, a single product can be linked to multiple pricing bundles or packages.</p>
                </div>
                <button
                  type="button"
                  onClick={() => setFormData({ ...formData, allowProductInMultipleBundles: !formData.allowProductInMultipleBundles })}
                  className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${formData.allowProductInMultipleBundles ? 'bg-blue-600' : 'bg-gray-200'}`}
                >
                  <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${formData.allowProductInMultipleBundles ? 'translate-x-5' : 'translate-x-1'}`} />
                </button>
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-3">
                <div>
                  <label className="block text-xs font-bold text-gray-900">Category Conflict Rules</label>
                  <p className="text-[10px] text-gray-500 font-medium">Define pairs of product categories that cannot be bundled together.</p>
                  <p className="text-[10px] text-blue-700 font-medium mt-1">
                    {hasExistingCategories
                      ? 'Suggestions include categories already used in products and existing conflict rules.'
                      : `No categories defined yet. Starter examples: ${CATEGORY_EXAMPLES.join(', ')}.`}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={addConflictRule}
                  className="bg-blue-600 text-white px-3 py-1.5 rounded-lg font-bold text-[10px] uppercase tracking-widest hover:bg-blue-700 transition shadow-md flex items-center"
                >
                  <Plus className="h-3 w-3 mr-1" /> Add Rule
                </button>
              </div>

              <div className="space-y-3">
                {formData.categoryConflictRules.map((rule: any, idx: number) => (
                  <div key={idx} className="space-y-1">
                    <div className={`flex items-center space-x-3 bg-white p-3 rounded-lg border shadow-sm animate-in fade-in slide-in-from-top-1 ${fieldErrors[`conflictRule_${idx}`] ? 'border-red-500' : 'border-gray-200'}`}>
                      <div className="flex-1">
                        <PlexusSelect
                          placeholder="Category A"
                          options={buildCategorySelectOptions(rule.categoryB)}
                          value={isCustomCategory(rule.categoryA, rule.categoryB)
                            ? { value: CREATE_NEW_CATEGORY, label: '+ Create New Category...' }
                            : (rule.categoryA ? { value: rule.categoryA, label: rule.categoryA } : null)}
                          onChange={(opt) => {
                            onConflictCategorySelect(idx, 'categoryA', opt);
                            if (fieldErrors[`conflictRule_${idx}`]) setFieldErrors({ ...fieldErrors, [`conflictRule_${idx}`]: '' });
                          }}
                        />
                        {isCustomCategory(rule.categoryA, rule.categoryB) && (
                          <input
                            type="text"
                            placeholder="New category code"
                            className="mt-2 w-full px-3 py-1.5 bg-gray-50 border border-gray-100 rounded-md text-[11px] font-bold uppercase focus:ring-1 focus:ring-blue-500 outline-none"
                            value={customConflictInputs[getCustomConflictKey(idx, 'categoryA')] ?? rule.categoryA}
                            onChange={(e) => {
                              onConflictCustomInputChange(idx, 'categoryA', e.target.value);
                              if (fieldErrors[`conflictRule_${idx}`]) setFieldErrors({ ...fieldErrors, [`conflictRule_${idx}`]: '' });
                            }}
                          />
                        )}
                      </div>
                      <div className="text-gray-400 font-bold text-[10px]">CONFLICTS WITH</div>
                      <div className="flex-1">
                        <PlexusSelect
                          placeholder="Category B"
                          options={buildCategorySelectOptions(rule.categoryA)}
                          value={isCustomCategory(rule.categoryB, rule.categoryA)
                            ? { value: CREATE_NEW_CATEGORY, label: '+ Create New Category...' }
                            : (rule.categoryB ? { value: rule.categoryB, label: rule.categoryB } : null)}
                          onChange={(opt) => {
                            onConflictCategorySelect(idx, 'categoryB', opt);
                            if (fieldErrors[`conflictRule_${idx}`]) setFieldErrors({ ...fieldErrors, [`conflictRule_${idx}`]: '' });
                          }}
                        />
                        {isCustomCategory(rule.categoryB, rule.categoryA) && (
                          <input
                            type="text"
                            placeholder="New category code"
                            className="mt-2 w-full px-3 py-1.5 bg-gray-50 border border-gray-100 rounded-md text-[11px] font-bold uppercase focus:ring-1 focus:ring-blue-500 outline-none"
                            value={customConflictInputs[getCustomConflictKey(idx, 'categoryB')] ?? rule.categoryB}
                            onChange={(e) => {
                              onConflictCustomInputChange(idx, 'categoryB', e.target.value);
                              if (fieldErrors[`conflictRule_${idx}`]) setFieldErrors({ ...fieldErrors, [`conflictRule_${idx}`]: '' });
                            }}
                          />
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => removeConflictRule(idx)}
                        className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-md transition"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                    {fieldErrors[`conflictRule_${idx}`] && (
                      <p className="px-1 text-[10px] text-red-500 font-bold">{fieldErrors[`conflictRule_${idx}`]}</p>
                    )}
                  </div>
                ))}
                {formData.categoryConflictRules.length === 0 && (
                  <div className="text-center py-6 bg-gray-50 border-2 border-dashed border-gray-200 rounded-xl">
                    <p className="text-[10px] font-bold text-gray-400 uppercase tracking-widest">No conflict rules defined</p>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {!isAdmin && (
          <div className="space-y-1">
            <div className={`bg-blue-50 p-4 rounded-xl border flex items-center justify-between ${fieldErrors.captchaAnswer ? 'border-red-500' : 'border-blue-100'}`}>
              <div>
                <p className="text-[10px] font-bold text-blue-900 mb-0.5 uppercase tracking-widest">Security Check</p>
                <p className="text-blue-700 text-base font-mono font-bold">{captcha.question}</p>
              </div>
              <input
                type="text"
                placeholder="?"
                className="w-16 px-3 py-2 rounded-lg border border-blue-200 focus:ring-2 focus:ring-blue-500 outline-none text-center font-bold text-sm"
                value={formData.captchaAnswer}
                onChange={e => {
                  setFormData({ ...formData, captchaAnswer: e.target.value });
                  if (fieldErrors.captchaAnswer) setFieldErrors({ ...fieldErrors, captchaAnswer: '' });
                }}
              />
            </div>
            {fieldErrors.captchaAnswer && <p className="mt-1 text-[10px] text-red-500 font-bold">{fieldErrors.captchaAnswer}</p>}
          </div>
        )}

        <div className={isAdmin ? "flex space-x-4 border-t border-gray-100 pt-6" : "flex space-x-3"}>
          {isAdmin && (
            <button
              type="button"
              onClick={() => navigate('/banks')}
              className="flex-1 px-4 py-3 border border-gray-100 rounded-xl font-bold text-gray-400 hover:bg-gray-50 hover:text-gray-600 transition uppercase tracking-widest text-[10px]"
            >
              Discard Changes
            </button>
          )}
          <button
            disabled={loading}
            type="submit"
            className={isAdmin
              ? "flex-1 px-4 py-3 bg-blue-600 text-white rounded-xl font-bold hover:bg-blue-700 transition shadow-lg shadow-blue-200 flex items-center justify-center uppercase tracking-widest text-[10px] disabled:opacity-50"
              : "flex-[2] bg-blue-600 text-white py-2.5 rounded-lg font-bold text-sm hover:bg-blue-700 transition shadow-md disabled:opacity-50 flex items-center justify-center"
            }
          >
            {loading ? <><Loader2 className="h-4 w-4 mr-1.5 animate-spin" /> {isEditing ? 'Updating...' : 'Submitting...'}</> :
             isEditing ? <><Save className="h-4 w-4 mr-1.5" /> Commit Configuration</> : isAdmin ? 'Commit New Bank' : 'Submit Request'}
          </button>
        </div>
      </form>
    </div>
  );

  if (isAdmin) {
    return (
      <AdminPage width="medium">
        <AdminFormHeader
          icon={Building2}
          title={loading ? 'Bank Settings' : isMyBank ? 'My Bank Settings' : isEditing ? (formData.name || formData.bankId || id) : 'New Bank'}
          description={isMyBank ? `Manage your institution's global configuration.` : isEditing ? `Updating configuration for ${formData.name || id}` : 'Fill out the form below to register a new bank in the system.'}
          onClose={() => navigate('/banks')}
        />
        <div className="bg-white rounded-xl shadow-sm overflow-hidden border border-gray-100 p-8">
          {renderForm()}
        </div>
      </AdminPage>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col md:flex-row">
      {/* Information Panel */}
      <div className="md:w-1/3 bg-blue-900 text-white p-12 flex flex-col">
        <div className="flex items-center space-x-2 mb-12 cursor-pointer" onClick={() => navigate('/')}>
          <img src="/logo-plexus.svg" alt="Plexus Logo" className="h-8 w-8 brightness-0 invert" />
          <span className="text-2xl font-bold tracking-tight">Plexus</span>
        </div>

        <div className="flex-1">
          <h1 className="text-4xl font-bold mb-8 leading-tight">Join the Modern Banking Revolution</h1>
          <p className="text-blue-100 text-lg mb-10 leading-relaxed">
            Plexus provides a flexible, component-based engine for managing global banking products and pricing.
          </p>

          <div className="space-y-8">
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-blue-800 rounded-lg"><Rocket className="h-6 w-6 text-blue-300" /></div>
              <div>
                <h3 className="font-bold text-xl mb-1">Rapid Onboarding</h3>
                <p className="text-blue-200 text-sm">Onboard your bank in minutes with our secret-less OIDC integration.</p>
              </div>
            </div>
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-blue-800 rounded-lg"><ShieldCheck className="h-6 w-6 text-blue-300" /></div>
              <div>
                <h3 className="font-bold text-xl mb-1">Secure Isolation</h3>
                <p className="text-blue-200 text-sm">Row-level data isolation ensures your product data is never exposed to other tenants.</p>
              </div>
            </div>
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-blue-800 rounded-lg"><Info className="h-6 w-6 text-blue-300" /></div>
              <div>
                <h3 className="font-bold text-xl mb-1">Flexible Pricing</h3>
                <p className="text-blue-200 text-sm">Manage complex tiered pricing and dynamic fees via a powerful rules engine.</p>
              </div>
            </div>
          </div>
        </div>

        <div className="mt-12 pt-8 border-t border-blue-800 text-blue-300 text-sm">
          © 2024 Plexus Bank Engine. All rights reserved.
        </div>
      </div>

      {/* Form Panel */}
      <div className="flex-1 p-8 md:p-20 flex items-center justify-center relative">
        {renderForm()}
      </div>
    </div>
  );
};

export default OnboardingPage;
