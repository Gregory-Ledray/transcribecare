import { motion } from 'motion/react';
import { Ear, MessageSquareText, Users, Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';

interface FeatureCardProps {
  icon: ReactNode;
  title: string;
  description: string;
}

function FeatureCard({ icon, title, description }: FeatureCardProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-50px' }}
      transition={{ duration: 0.5 }}
      className="bg-surface-container rounded-xl p-8 flex flex-col gap-4"
    >
      <div className="w-14 h-14 rounded-lg bg-secondary-container flex items-center justify-center text-on-secondary-container">
        {icon}
      </div>
      <h3 className="text-xl font-bold text-on-background">{title}</h3>
      <p className="text-on-surface-variant text-lg leading-relaxed">{description}</p>
    </motion.div>
  );
}

export default function HomePage() {
  return (
    <div className="min-h-screen bg-background">
      {/* Navigation */}
      <nav className="fixed top-0 w-full bg-surface/90 backdrop-blur-sm z-50 border-b border-outline-variant">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link to="/" className="text-xl font-bold text-primary">TranscribeCare</Link>
          <Link
            to="/app"
            className="bg-primary text-on-primary px-5 py-2.5 rounded-lg font-semibold text-sm hover:opacity-90 transition-opacity"
          >
            Open App
          </Link>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="pt-32 pb-20 px-6">
        <div className="max-w-4xl mx-auto text-center">
          <motion.h1
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="text-4xl md:text-6xl font-bold text-primary leading-tight"
          >
            Overcome Hearing Loss
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.15 }}
            className="mt-6 text-xl md:text-2xl text-on-surface-variant leading-relaxed max-w-3xl mx-auto"
          >
            Transcribe and record doctors, caregivers, and more while sharing
            what you hear with loved ones.
          </motion.p>
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="mt-10 flex flex-col sm:flex-row gap-4 justify-center"
          >
            <Link
              to="/app"
              className="bg-secondary text-on-secondary px-8 py-4 rounded-xl font-bold text-lg hover:opacity-90 transition-opacity inline-block"
            >
              Try It Now
            </Link>
          </motion.div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20 px-6 bg-surface">
        <div className="max-w-6xl mx-auto">
          <h2 className="text-3xl md:text-4xl font-bold text-center text-on-background mb-16">
            Built for patients and caregivers
          </h2>
          <div className="grid md:grid-cols-2 gap-8">
            <FeatureCard
              icon={<MessageSquareText size={28} />}
              title="Understand Conversations in Real Time"
              description="Real-time transcription appears as you and your doctor talk, helping you understand what they are saying without needing to read their lips."
            />
            <FeatureCard
              icon={<Users size={28} />}
              title="Keep Family in the Loop"
              description="Transcriptions are shareable with loved ones, saving patients and their families time in care coordination."
            />
            <FeatureCard
              icon={<Search size={28} />}
              title="Search Past Conversations"
              description="Transcriptions are searchable and audio is replayable so you can find and re-listen to important parts of the conversation."
            />
            <FeatureCard
              icon={<Ear size={28} />}
              title="Designed for Accessibility"
              description="High-contrast visuals, large text, and simple controls make TranscribeCare easy to use for elderly and visually impaired patients."
            />
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 px-6">
        <div className="max-w-3xl mx-auto text-center">
          <h2 className="text-3xl md:text-4xl font-bold text-primary mb-6">
            Never miss what your doctor says
          </h2>
          <p className="text-lg text-on-surface-variant mb-10 leading-relaxed">
            TranscribeCare gives patients and caregivers confidence that every
            word is captured, understood, and shareable.
          </p>
          <Link
            to="/app"
            className="bg-primary text-on-primary px-8 py-4 rounded-xl font-bold text-lg hover:opacity-90 transition-opacity inline-block"
          >
            Get Started
          </Link>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-outline-variant py-8 px-6">
        <div className="max-w-6xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <span className="text-on-surface-variant text-sm">
            &copy; {new Date().getFullYear()} TranscribeCare
          </span>
          <a
            href="/PRIVACY_POLICY.md"
            className="text-on-surface-variant text-sm hover:text-primary transition-colors"
          >
            Privacy Policy
          </a>
        </div>
      </footer>
    </div>
  );
}
