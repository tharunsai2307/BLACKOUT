import React, { useEffect, useRef } from 'react';
import maplibregl, { Map as MapLibreMap, Marker, Popup, NavigationControl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { EmergencyAlert } from '../../types/database';
import { formatCoordinates, formatRelativeTime } from '../../utils/formatters';

interface EmergencyMapProps {
  incidents: EmergencyAlert[];
  selectedIncident?: EmergencyAlert | null;
  onSelectIncident?: (incident: EmergencyAlert) => void;
  className?: string;
}

export const EmergencyMap: React.FC<EmergencyMapProps> = ({
  incidents,
  selectedIncident,
  onSelectIncident,
  className = 'h-full w-full',
}) => {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);

  // Filter incidents with valid GPS coordinates
  const validIncidents = incidents.filter(
    (inc) =>
      inc.latitude != null &&
      inc.longitude != null &&
      !(inc.latitude === 0 && inc.longitude === 0)
  );

  // Initialize Map
  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return;

    // Use OpenStreetMap free raster tile source
    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: {
        version: 8,
        sources: {
          'osm-tiles': {
            type: 'raster',
            tiles: [
              'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            ],
            tileSize: 256,
            attribution: '&copy; OpenStreetMap contributors | BLACKOUT OPS',
          },
        },
        layers: [
          {
            id: 'osm-tiles-layer',
            type: 'raster',
            source: 'osm-tiles',
            minzoom: 0,
            maxzoom: 19,
          },
        ],
      },
      center: [77.2090, 28.6139], // Default center
      zoom: 10,
    });

    map.addControl(new NavigationControl({ showCompass: true }), 'top-right');
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Update Markers
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    // Clear existing markers
    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];

    if (validIncidents.length === 0) return;

    // Center map on bounds if incidents exist
    const bounds = new maplibregl.LngLatBounds();

    validIncidents.forEach((incident) => {
      const lng = incident.longitude!;
      const lat = incident.latitude!;
      bounds.extend([lng, lat]);

      // Create custom tactical marker element
      const el = document.createElement('div');
      el.className = 'cursor-pointer group relative';

      const isCritical = incident.priority === 'CRITICAL';
      const isHigh = incident.priority === 'HIGH';
      const markerColor = isCritical ? '#EF4444' : isHigh ? '#F59E0B' : '#06B6D4';

      el.innerHTML = `
        <div style="background-color: ${markerColor};" class="w-6 h-6 rounded-full flex items-center justify-center text-white border-2 border-slate-900 shadow-lg ${
          isCritical ? 'animate-pulse' : ''
        }">
          <span style="font-size: 10px; font-weight: bold; font-family: monospace;">!</span>
        </div>
        ${
          isCritical
            ? `<div style="border-color: ${markerColor};" class="absolute -inset-1 rounded-full border animate-ping pointer-events-none opacity-75"></div>`
            : ''
        }
      `;

      // Popup
      const popupHtml = `
        <div style="color: #0F172A; font-family: monospace; padding: 4px; font-size: 11px;">
          <div style="font-weight: bold; color: ${markerColor}; margin-bottom: 2px;">
            [${incident.priority}] ${incident.category}
          </div>
          <div style="color: #475569; font-size: 10px; margin-bottom: 4px;">
            ID: ${incident.message_id.slice(0, 8)} • ${formatRelativeTime(incident.created_at)}
          </div>
          <div style="margin-bottom: 4px; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            ${incident.description || 'No notes attached'}
          </div>
          <div style="color: #64748B; font-size: 9px; margin-bottom: 6px;">
            ${formatCoordinates(incident.latitude, incident.longitude, incident.accuracy_meters)}
          </div>
          <div style="display: flex; justify-content: space-between; font-size: 9px; border-top: 1px solid #E2E8F0; padding-top: 4px;">
            <span>Hops: ${incident.hop_count}</span>
            <span>Status: <strong>${incident.verification_status}</strong></span>
          </div>
        </div>
      `;

      const popup = new Popup({ offset: 15, closeButton: false }).setHTML(popupHtml);

      const marker = new Marker({ element: el })
        .setLngLat([lng, lat])
        .setPopup(popup)
        .addTo(map);

      el.addEventListener('click', () => {
        if (onSelectIncident) {
          onSelectIncident(incident);
        }
      });

      markersRef.current.push(marker);
    });

    // Fit bounds if we have multiple points and no specific incident selected
    if (!selectedIncident && validIncidents.length > 0) {
      map.fitBounds(bounds, { padding: 60, maxZoom: 14 });
    }
  }, [validIncidents, onSelectIncident]);

  // Fly to selected incident if specified
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !selectedIncident) return;

    if (
      selectedIncident.latitude != null &&
      selectedIncident.longitude != null &&
      !(selectedIncident.latitude === 0 && selectedIncident.longitude === 0)
    ) {
      map.flyTo({
        center: [selectedIncident.longitude, selectedIncident.latitude],
        zoom: 15,
        essential: true,
      });
    }
  }, [selectedIncident]);

  const noGpsCount = incidents.length - validIncidents.length;

  return (
    <div className={`relative overflow-hidden rounded border border-operations-border bg-operations-surface ${className}`}>
      <div ref={mapContainerRef} className="h-full w-full" />

      {/* Floating GPS Coverage Info Badge */}
      <div className="absolute bottom-3 left-3 bg-operations-card/90 backdrop-blur border border-operations-border px-3 py-1.5 rounded text-[11px] font-mono text-operations-muted z-10 flex items-center space-x-3 shadow-lg">
        <span className="text-white font-bold">
          Mapped: <span className="text-operations-cyan">{validIncidents.length}</span> / {incidents.length}
        </span>
        {noGpsCount > 0 && (
          <span className="text-operations-amber">
            ({noGpsCount} alerts lacking GPS fix)
          </span>
        )}
      </div>
    </div>
  );
};
